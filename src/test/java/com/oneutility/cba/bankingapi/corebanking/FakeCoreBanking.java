package com.oneutility.cba.bankingapi.corebanking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An in-memory stand-in for CBA-CORE and the auth service's {@code cba} realm token endpoint, on a real port, for
 * integration tests. It answers the calls the Banking API makes, in Fineract's shapes, and refuses what CBA-CORE
 * refuses (a BVN or mobile number already in use, an unknown product, a missing token).
 */
public final class FakeCoreBanking implements AutoCloseable {

    public static final String TOKEN = "core-banking-test-token";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern DATATABLE = Pattern.compile("/datatables/([^/]+)/(\\d+)");

    private final HttpServer server;
    private final AtomicLong ids = new AtomicLong();
    private final AtomicInteger tokensIssued = new AtomicInteger();
    private final Map<String, Map<String, Long>> codes = new LinkedHashMap<>();
    private final Map<Long, ObjectNode> clients = new LinkedHashMap<>();
    private final Map<Long, ObjectNode> savings = new LinkedHashMap<>();
    private final Map<Long, ArrayNode> addresses = new LinkedHashMap<>();
    private final Map<Long, ArrayNode> identifiers = new LinkedHashMap<>();
    private final Map<String, Map<Long, ArrayNode>> datatables = new LinkedHashMap<>();
    private final Map<String, JsonNode> idempotent = new LinkedHashMap<>();
    /** Holds by transaction ID. */
    private final Map<Long, ObjectNode> holds = new LinkedHashMap<>();
    private volatile boolean down;
    /** The current request's decoded query string (handle is synchronized). */
    private String query = "";
    /** Savings products: id → name, NUBAN prefix, virtual account type (or null). */
    private final Map<Long, String[]> products = new LinkedHashMap<>(Map.of(1L,
            new String[] { "Individual Savings", "002", null }, 3L, new String[] { "Current Account", "005", null }, 4L,
            new String[] { "Smart Transfer Account", "600", "SMART_TRANSFER" }, 5L,
            new String[] { "Virtual Accounts", "555", "AIRTIME" }));
    /** Virtual accounts by savings ID. */
    private final Map<Long, ObjectNode> virtualAccounts = new LinkedHashMap<>();

    public FakeCoreBanking() throws IOException {
        code("Customer Identifier", "Passport", "BVN", "NIN", "CAC Registration Number", "TIN");
        code("ADDRESS_TYPE", "Residential", "Business");
        code("STATE", "Lagos", "Abia", "Federal Capital Territory");
        code("COUNTRY", "Nigeria", "Ghana");
        code("KycLevel", "1", "2", "3");
        code("RiskRating", "Low", "Medium", "High");
        code("RelatedPersonRole", "Director", "Signatory", "Director & Signatory");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    /** CBA-CORE's API base, as {@code banking-api.core-banking.base-url}. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/fineract-provider/api/v1";
    }

    /** The {@code cba} realm token endpoint, as {@code banking-api.core-banking.token-url}. */
    public String tokenUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/cba/protocol/openid-connect/token";
    }

    /** Makes every CBA-CORE call fail as if CBA-CORE were down (the token endpoint still answers). */
    public void setDown(boolean down) {
        this.down = down;
    }

    public int tokensIssued() {
        return tokensIssued.get();
    }

    public synchronized int clientCount() {
        return clients.size();
    }

    /** Records {@code owner} as the client a customer belongs to, as a migration or staff would. */
    public synchronized void setOwner(long clientId, String owner) {
        ObjectNode row = JSON.createObjectNode().put("client_id", clientId).put("Owner", owner);
        datatables.computeIfAbsent("Customer Owner", t -> new LinkedHashMap<>()).put(clientId, JSON.createArrayNode().add(row));
    }

    /** Sets an account's state as CBA-CORE would report it: {@code active}, {@code closed}, {@code dormant}, {@code blocked}, {@code pending}. */
    public synchronized void setAccountState(long savingsId, String state) {
        ObjectNode account = savings.get(savingsId);
        ObjectNode status = (ObjectNode) account.get("status");
        ObjectNode subStatus = (ObjectNode) account.get("subStatus");
        status.put("active", state.equals("active") || state.equals("dormant") || state.equals("blocked"))
                .put("closed", state.equals("closed"));
        subStatus.put("dormant", state.equals("dormant")).put("blockDebit", state.equals("blocked"));
    }

    /** Sets an account's ledger and available balances. */
    public synchronized void setBalances(long savingsId, String ledger, String available) {
        ((ObjectNode) savings.get(savingsId).get("summary")).put("accountBalance", new java.math.BigDecimal(ledger))
                .put("availableBalance", new java.math.BigDecimal(available));
    }

    /** The ID of the account with this number. */
    public synchronized long savingsId(String accountNumber) {
        return savings.values().stream().filter(a -> accountNumber.equals(a.path("accountNo").asText()))
                .mapToLong(a -> a.path("id").asLong()).findFirst().orElseThrow();
    }

    private ObjectNode newAccount(long savingsId, long clientId, long productId) {
        String[] product = products.get(productId);
        ObjectNode client = clients.get(clientId);
        String name = client.hasNonNull("fullname") ? client.get("fullname").asText()
                : (client.path("firstname").asText() + " " + client.path("lastname").asText()).trim();
        // CBA-CORE numbers accounts on products with a NUBAN prefix as NUBANs
        ObjectNode account = JSON.createObjectNode().put("id", savingsId)
                .put("accountNo", product[1] + String.format("%06d", savingsId) + "0").put("clientId", clientId)
                .put("clientName", name).put("savingsProductId", productId).put("savingsProductName", product[0]);
        account.putObject("status").put("id", 300).put("code", "savingsAccountStatusType.active").put("active", true)
                .put("closed", false);
        account.putObject("subStatus").put("id", 0).put("dormant", false).put("block", false).put("blockCredit", false)
                .put("blockDebit", false);
        account.putObject("currency").put("code", "NGN").put("decimalPlaces", 2);
        account.putObject("summary").put("accountBalance", 0.0).put("availableBalance", 0.0);
        savings.put(savingsId, account);
        return account;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void code(String name, String... values) {
        Map<String, Long> byName = new LinkedHashMap<>();
        for (String value : values) {
            byName.put(value, ids.incrementAndGet() + 1000);
        }
        codes.put(name, byName);
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = URLDecoder.decode(exchange.getRequestURI().getRawPath(), StandardCharsets.UTF_8);
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            if (path.equals("/realms/cba/protocol/openid-connect/token")) {
                tokensIssued.incrementAndGet();
                send(exchange, 200, JSON.createObjectNode().put("access_token", TOKEN).put("expires_in", 300));
                return;
            }
            if (down) {
                exchange.close();
                return;
            }
            if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    || !"default".equals(exchange.getRequestHeaders().getFirst("Fineract-Platform-TenantId"))) {
                send(exchange, 401, error(401, "error.msg.not.authenticated", "Unauthenticated"));
                return;
            }
            String api = path.substring("/fineract-provider/api/v1".length());
            String rawQuery = exchange.getRequestURI().getRawQuery();
            query = rawQuery == null ? "" : URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
            JsonNode body = requestBody.length == 0 ? null : JSON.readTree(requestBody);
            Object[] answer = route(method, api, body, exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            send(exchange, (Integer) answer[0], (JsonNode) answer[1]);
        }
    }

    private Object[] route(String method, String api, JsonNode body, String idempotencyKey) {
        if (method.equals("GET") && api.equals("/codes")) {
            ArrayNode list = JSON.createArrayNode();
            int id = 1;
            for (String name : codes.keySet()) {
                list.addObject().put("id", id++).put("name", name);
            }
            return ok(list);
        }
        Matcher m;
        if (method.equals("GET") && (m = Pattern.compile("/codes/(\\d+)/codevalues").matcher(api)).matches()) {
            String name = new ArrayList<>(codes.keySet()).get(Integer.parseInt(m.group(1)) - 1);
            ArrayNode values = JSON.createArrayNode();
            codes.get(name).forEach((value, id) -> values.addObject().put("id", id).put("name", value).put("active", true));
            return ok(values);
        }
        if (method.equals("POST") && api.equals("/oneubank/customers")) {
            if (idempotencyKey != null && idempotent.containsKey(idempotencyKey)) {
                return ok(idempotent.get(idempotencyKey));
            }
            Object[] result = createCustomer(body);
            if (idempotencyKey != null && (Integer) result[0] == 200) {
                idempotent.put(idempotencyKey, (JsonNode) result[1]);
            }
            return result;
        }
        if (method.equals("PUT") && (m = Pattern.compile("/oneubank/customers/(\\d+)").matcher(api)).matches()) {
            return updateCustomer(Long.parseLong(m.group(1)), body);
        }
        if (method.equals("POST") && (m = Pattern.compile("/oneubank/customers/(\\d+)/accounts").matcher(api)).matches()) {
            if (idempotencyKey != null && idempotent.containsKey(idempotencyKey)) {
                return ok(idempotent.get(idempotencyKey));
            }
            long clientId = Long.parseLong(m.group(1));
            Object[] refused = unsupported(body, Set.of("productId"));
            if (refused != null) {
                return refused;
            }
            if (!clients.containsKey(clientId)) {
                return new Object[] { 404, error(404, "error.msg.client.id.invalid", "No client") };
            }
            if (!products.containsKey(body.path("productId").asLong())) {
                // Fineract's own code for this is spelt "savingproduct"
                return new Object[] { 404, error(404, "error.msg.savingproduct.id.invalid", "Saving product does not exist") };
            }
            long savingsId = ids.incrementAndGet();
            newAccount(savingsId, clientId, body.path("productId").asLong());
            ObjectNode result = JSON.createObjectNode().put("clientId", clientId).put("savingsId", savingsId)
                    .put("resourceId", savingsId);
            if (idempotencyKey != null) {
                idempotent.put(idempotencyKey, result);
            }
            return ok(result);
        }
        if (method.equals("GET") && api.equals("/search")) {
            String number = query.replaceAll(".*(?:^|&)query=([^&]*).*", "$1");
            ArrayNode hits = JSON.createArrayNode();
            savings.values().stream().filter(a -> number.equals(a.path("accountNo").asText()))
                    .forEach(a -> hits.addObject().put("entityId", a.path("id").asLong())
                            .put("entityAccountNo", a.path("accountNo").asText()).put("entityType", "SAVING")
                            .put("parentId", a.path("clientId").asLong()).put("parentType", "client"));
            return ok(hits);
        }
        if (method.equals("GET") && api.equals("/savingsproducts")) {
            ArrayNode list = JSON.createArrayNode();
            products.forEach((id, p) -> list.addObject().put("id", id).put("name", p[0]).put("nubanPrefix", p[1])
                    .put("enforceTierLimits", true).putObject("currency").put("code", "NGN"));
            return ok(list);
        }
        if (method.equals("GET") && (m = Pattern.compile("/clients/(\\d+)/accounts").matcher(api)).matches()) {
            long clientId = Long.parseLong(m.group(1));
            ObjectNode result = JSON.createObjectNode();
            ArrayNode list = result.putArray("savingsAccounts");
            savings.values().stream().filter(a -> a.path("clientId").asLong() == clientId)
                    .forEach(a -> list.addObject().put("id", a.path("id").asLong()).put("accountNo", a.path("accountNo").asText())
                            .put("productId", a.path("savingsProductId").asLong()));
            return ok(result);
        }
        if (method.equals("POST") && api.equals("/oneubank/customers/search")) {
            return ok(search(body));
        }
        if ((m = Pattern.compile("/clients/(\\d+)").matcher(api)).matches() && method.equals("GET")) {
            ObjectNode client = clients.get(Long.parseLong(m.group(1)));
            return client == null ? new Object[] { 404, error(404, "error.msg.client.id.invalid", "No client") } : ok(client);
        }
        if ((m = Pattern.compile("/client/(\\d+)/addresses").matcher(api)).matches()) {
            return ok(addresses.getOrDefault(Long.parseLong(m.group(1)), JSON.createArrayNode()));
        }
        if ((m = Pattern.compile("/clients/(\\d+)/identifiers").matcher(api)).matches()) {
            return ok(identifiers.getOrDefault(Long.parseLong(m.group(1)), JSON.createArrayNode()));
        }
        if (method.equals("POST") && (m = Pattern.compile("/savingsaccounts/(\\d+)/transactions").matcher(api)).matches()
                && query.equals("command=holdAmount")) {
            return holdAmount(Long.parseLong(m.group(1)), body);
        }
        if (method.equals("POST") && (m = Pattern.compile("/savingsaccounts/(\\d+)/transactions/(\\d+)").matcher(api)).matches()
                && query.equals("command=releaseAmount")) {
            return releaseAmount(Long.parseLong(m.group(1)), Long.parseLong(m.group(2)));
        }
        if (method.equals("POST") && (m = Pattern.compile("/savingsaccounts/(\\d+)").matcher(api)).matches()) {
            return blockCommand(Long.parseLong(m.group(1)), query.replace("command=", ""), body);
        }
        if (api.startsWith("/oneubank/virtual-accounts")) {
            return virtualAccountRoute(method, api, body, idempotencyKey);
        }
        if (method.equals("GET") && api.equals("/oneubank/holds")) {
            return findHold();
        }
        if ((m = Pattern.compile("/savingsaccounts/(\\d+)").matcher(api)).matches()) {
            ObjectNode account = savings.get(Long.parseLong(m.group(1)));
            return account == null ? new Object[] { 404, error(404, "error.msg.saving.account.id.invalid", "No account") }
                    : ok(account);
        }
        if ((m = DATATABLE.matcher(api)).matches()) {
            Map<Long, ArrayNode> table = datatables.computeIfAbsent(m.group(1), t -> new LinkedHashMap<>());
            long clientId = Long.parseLong(m.group(2));
            if (method.equals("GET")) {
                return ok(table.getOrDefault(clientId, JSON.createArrayNode()));
            }
            ObjectNode row = row(body, clientId);
            if (method.equals("POST")) {
                table.computeIfAbsent(clientId, c -> JSON.createArrayNode()).add(row);
            } else {
                ArrayNode rows = table.get(clientId);
                if (rows == null || rows.isEmpty()) {
                    return new Object[] { 404, error(404, "error.msg.datatable.entry.not.found", "No row") };
                }
                ((ObjectNode) rows.get(0)).setAll(row);
            }
            return ok(JSON.createObjectNode().put("resourceId", clientId));
        }
        return new Object[] { 404, error(404, "error.msg.resource.not.found", "No fake for " + method + " " + api) };
    }

    /** Fineract refuses parameters it does not know, as this does for the customer calls. */
    private static Object[] unsupported(JsonNode object, Set<String> known) {
        java.util.Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!known.contains(name)) {
                return new Object[] { 400, error(400, "error.msg.parameter.unsupported",
                        "The parameter `" + name + "` is not supported") };
            }
        }
        return null;
    }

    private static final Set<String> CLIENT_FIELDS = Set.of("officeId", "legalFormId", "firstname", "middlename",
            "lastname", "fullname", "dateOfBirth", "emailAddress", "mobileNo", "active", "activationDate",
            "submittedOnDate", "savingsProductId", "address", "datatables", "locale", "dateFormat");

    private Object[] createCustomer(JsonNode body) {
        JsonNode client = body.path("client");
        Object[] refused = unsupported(body, Set.of("client", "identifiers"));
        refused = refused != null ? refused : unsupported(client, CLIENT_FIELDS);
        refused = refused != null ? refused
                : refusedDuplicate(client.path("mobileNo").asText(null), body.path("identifiers"), null);
        if (refused != null) {
            return refused;
        }
        if (client.path("savingsProductId").asLong() != 1) {
            return new Object[] { 404, error(404, "error.msg.savingsproduct.id.invalid", "Saving product does not exist") };
        }
        long clientId = ids.incrementAndGet();
        long savingsId = ids.incrementAndGet();
        boolean business = client.path("legalFormId").asInt() == 2;
        ObjectNode stored = JSON.createObjectNode().put("id", clientId);
        for (String field : List.of("firstname", "middlename", "lastname", "fullname", "mobileNo", "emailAddress")) {
            if (client.hasNonNull(field)) {
                stored.put(field, client.get(field).asText());
            }
        }
        if (client.hasNonNull("dateOfBirth")) {
            String[] parts = client.get("dateOfBirth").asText().split("-");
            stored.putArray("dateOfBirth").add(Integer.parseInt(parts[0])).add(Integer.parseInt(parts[1]))
                    .add(Integer.parseInt(parts[2]));
        }
        stored.putObject("legalForm").put("id", business ? 2 : 1);
        stored.putObject("status").put("code", "clientStatusType.active");
        clients.put(clientId, stored);
        newAccount(savingsId, clientId, client.path("savingsProductId").asLong());

        ArrayNode addressList = addresses.computeIfAbsent(clientId, c -> JSON.createArrayNode());
        for (JsonNode address : client.path("address")) {
            ObjectNode a = ((ObjectNode) address.deepCopy()).put("addressId", ids.incrementAndGet());
            a.put("stateName", name("STATE", address.path("stateProvinceId").asLong()));
            a.put("countryName", name("COUNTRY", address.path("countryId").asLong()));
            addressList.add(a);
        }
        ArrayNode identifierList = identifiers.computeIfAbsent(clientId, c -> JSON.createArrayNode());
        for (JsonNode identifier : body.path("identifiers")) {
            ObjectNode i = identifierList.addObject().put("id", ids.incrementAndGet())
                    .put("documentKey", identifier.path("documentKey").asText())
                    .put("status", "clientIdentifierStatusType.active");
            i.putObject("documentType").put("id", identifier.path("documentTypeId").asLong())
                    .put("name", name("Customer Identifier", identifier.path("documentTypeId").asLong()));
        }
        for (JsonNode table : client.path("datatables")) {
            datatables.computeIfAbsent(table.path("registeredTableName").asText(), t -> new LinkedHashMap<>())
                    .computeIfAbsent(clientId, c -> JSON.createArrayNode()).add(row(table.path("data"), clientId));
        }
        return ok(JSON.createObjectNode().put("clientId", clientId).put("savingsId", savingsId).put("resourceId", clientId));
    }

    /** Another customer's mobile number or identifier: refused as CBA-CORE does. */
    private Object[] refusedDuplicate(String mobile, JsonNode newIdentifiers, Long exceptClientId) {
        for (ObjectNode existing : clients.values()) {
            if (mobile != null && mobile.equals(existing.path("mobileNo").asText(null))
                    && existing.path("id").asLong() != (exceptClientId == null ? -1 : exceptClientId)) {
                return new Object[] { 403, error(403, "error.msg.client.duplicate.mobileNo",
                        "Client with mobileNo `" + mobile + "` already exists") };
            }
        }
        for (JsonNode identifier : newIdentifiers) {
            String key = identifier.path("documentKey").asText();
            for (Map.Entry<Long, ArrayNode> list : identifiers.entrySet()) {
                if (list.getKey().equals(exceptClientId)) {
                    continue;
                }
                for (JsonNode existing : list.getValue()) {
                    if (existing.path("documentKey").asText().equals(key)
                            && existing.path("documentType").path("id").asLong() == identifier.path("documentTypeId").asLong()) {
                        // On an update Fineract's message names no type ("type null"); on a create it does
                        String type = exceptClientId == null ? existing.path("documentType").path("name").asText() : "null";
                        ObjectNode refused = error(403, "validation.msg.domain.rule.violation", "Errors contain reason for domain rule violation.");
                        ((ArrayNode) refused.get("errors")).addObject()
                                .put("userMessageGlobalisationCode", "error.msg.clientIdentifier.identityKey.duplicate")
                                .put("defaultUserMessage", "Client identifier of type " + type + " with value of " + key + " already exists.");
                        return new Object[] { 403, refused };
                    }
                }
            }
        }
        return null;
    }

    /** Checks everything first, then changes everything: all or nothing, like CBA-CORE's transaction. */
    private Object[] updateCustomer(long clientId, JsonNode body) {
        ObjectNode stored = clients.get(clientId);
        if (stored == null) {
            return new Object[] { 404, error(404, "error.msg.client.id.invalid", "No client") };
        }
        JsonNode client = body.path("client");
        Object[] refused = unsupported(body, Set.of("client", "identifiers", "address", "datatableRows"));
        refused = refused != null ? refused : unsupported(client, CLIENT_FIELDS);
        refused = refused != null ? refused
                : refusedDuplicate(client.path("mobileNo").asText(null), body.path("identifiers"), clientId);
        if (refused != null) {
            return refused;
        }
        // Like Fineract, a person's name changes only as a whole
        if ((client.has("firstname") || client.has("middlename") || client.has("lastname"))
                && (!client.hasNonNull("firstname") || !client.hasNonNull("lastname"))) {
            return new Object[] { 400, error(400, "validation.msg.client.lastname.cannot.be.blank",
                    "The parameter `lastname` is mandatory.") };
        }
        for (String field : List.of("firstname", "middlename", "lastname", "fullname", "mobileNo", "emailAddress")) {
            if (client.hasNonNull(field)) {
                stored.put(field, client.get(field).asText());
            }
        }
        if (client.hasNonNull("dateOfBirth")) {
            String[] parts = client.get("dateOfBirth").asText().split("-");
            stored.putArray("dateOfBirth").add(Integer.parseInt(parts[0])).add(Integer.parseInt(parts[1]))
                    .add(Integer.parseInt(parts[2]));
        }
        ArrayNode identifierList = identifiers.computeIfAbsent(clientId, c -> JSON.createArrayNode());
        for (JsonNode identifier : body.path("identifiers")) {
            long typeId = identifier.path("documentTypeId").asLong();
            for (int i = identifierList.size() - 1; i >= 0; i--) {
                if (identifierList.get(i).path("documentType").path("id").asLong() == typeId) {
                    identifierList.remove(i);
                }
            }
            ObjectNode i = identifierList.addObject().put("id", ids.incrementAndGet())
                    .put("documentKey", identifier.path("documentKey").asText())
                    .put("status", "clientIdentifierStatusType.active");
            i.putObject("documentType").put("id", typeId).put("name", name("Customer Identifier", typeId));
        }
        if (body.has("address")) {
            JsonNode address = body.get("address");
            ObjectNode a = ((ObjectNode) address.deepCopy()).put("addressId", ids.incrementAndGet());
            a.put("stateName", name("STATE", address.path("stateProvinceId").asLong()));
            a.put("countryName", name("COUNTRY", address.path("countryId").asLong()));
            addresses.put(clientId, JSON.createArrayNode().add(a));
        }
        for (JsonNode table : body.path("datatableRows")) {
            ArrayNode rows = JSON.createArrayNode();
            for (JsonNode row : table.path("rows")) {
                rows.add(row(row, clientId));
            }
            datatables.computeIfAbsent(table.path("registeredTableName").asText(), t -> new LinkedHashMap<>()).put(clientId, rows);
        }
        return ok(JSON.createObjectNode().put("clientId", clientId).put("resourceId", clientId));
    }

    private Object[] holdAmount(long savingsId, JsonNode body) {
        Object[] refused = unsupported(body, Set.of("transactionAmount", "transactionDate", "reasonForBlock", "externalId",
                "locale", "dateFormat"));
        if (refused != null) {
            return refused;
        }
        ObjectNode account = savings.get(savingsId);
        if (!account.path("status").path("active").asBoolean()) {
            return new Object[] { 403, error(403, "error.msg.savingsaccount.transaction.account.is.not.active", "Account is not active") };
        }
        java.math.BigDecimal amount = new java.math.BigDecimal(body.path("transactionAmount").asText());
        ObjectNode summary = (ObjectNode) account.get("summary");
        java.math.BigDecimal available = summary.path("availableBalance").decimalValue();
        if (amount.compareTo(available) > 0) {
            return new Object[] { 403, error(403, "error.msg.savingsaccount.transaction.insufficient.account.balance",
                    "Insufficient account balance") };
        }
        String reference = body.path("externalId").asText(null);
        if (reference != null && holds.values().stream().anyMatch(h -> reference.equals(h.path("transactionReference").asText(null)))) {
            return new Object[] { 403, error(403, "error.msg.savings.transaction.external.id.duplicate", "Duplicate external id") };
        }
        long id = ids.incrementAndGet();
        holds.put(id, JSON.createObjectNode().put("holdTransactionId", id).put("savingsId", savingsId)
                .put("accountNo", account.path("accountNo").asText()).put("clientId", account.path("clientId").asLong())
                .put("amount", amount).put("reason", body.path("reasonForBlock").asText())
                .put("transactionReference", reference).put("createdOn", "2026-11-23T09:15:00Z").put("released", false)
                .put("reversed", false));
        summary.put("availableBalance", available.subtract(amount));
        return ok(JSON.createObjectNode().put("savingsId", savingsId).put("resourceId", id));
    }

    private Object[] releaseAmount(long savingsId, long holdId) {
        ObjectNode hold = holds.get(holdId);
        if (hold == null || hold.path("savingsId").asLong() != savingsId) {
            return new Object[] { 404, error(404, "error.msg.saving.account.trasaction.id.invalid", "No such transaction") };
        }
        if (hold.path("released").asBoolean()) {
            return new Object[] { 403, error(403, "error.msg.saving.account.transaction.release.already", "Already released") };
        }
        long id = ids.incrementAndGet();
        hold.put("released", true).put("releaseTransactionId", id).put("releasedOn", "2026-11-23T10:00:00Z");
        ObjectNode summary = (ObjectNode) savings.get(savingsId).get("summary");
        summary.put("availableBalance", summary.path("availableBalance").decimalValue().add(hold.path("amount").decimalValue()));
        return ok(JSON.createObjectNode().put("savingsId", savingsId).put("resourceId", id));
    }

    /** Fineract's block commands: the debit and credit blocks combine, and repeating one is refused. */
    private Object[] blockCommand(long savingsId, String command, JsonNode body) {
        ObjectNode account = savings.get(savingsId);
        ObjectNode sub = (ObjectNode) account.get("subStatus");
        boolean debit = sub.path("block").asBoolean() || sub.path("blockDebit").asBoolean();
        boolean credit = sub.path("block").asBoolean() || sub.path("blockCredit").asBoolean();
        boolean blocking = command.startsWith("block");
        boolean wantDebit = !command.endsWith("Credit");
        boolean wantCredit = !command.endsWith("Debit");
        if (blocking && (wantDebit && debit && (!wantCredit || credit) || wantCredit && credit && !wantDebit)
                || !blocking && (wantDebit && !wantCredit && !debit || wantCredit && !wantDebit && !credit || wantDebit && wantCredit && !(debit && credit))) {
            return new Object[] { 400, error(400, "validation.msg.savingsaccount.substatus.currently.set", "Already in that state") };
        }
        if (blocking && (body == null || body.path("reasonForBlock").asText("").isBlank())) {
            return new Object[] { 400, error(400, "validation.msg.savingsaccount.reasonForBlock.cannot.be.blank", "Reason is mandatory") };
        }
        boolean newDebit = blocking ? debit || wantDebit : debit && !wantDebit;
        boolean newCredit = blocking ? credit || wantCredit : credit && !wantCredit;
        sub.put("block", newDebit && newCredit).put("blockDebit", newDebit && !newCredit).put("blockCredit", newCredit && !newDebit);
        if (blocking) {
            account.put("reasonForBlock", body.path("reasonForBlock").asText());
        }
        return ok(JSON.createObjectNode().put("savingsId", savingsId).put("resourceId", savingsId));
    }

    private Object[] findHold() {
        String reference = query.replaceAll(".*(?:^|&)transactionReference=([^&]*).*", "$1");
        java.util.Optional<ObjectNode> hold = query.contains("transactionReference=")
                ? holds.values().stream().filter(h -> reference.equals(h.path("transactionReference").asText(null))).findFirst()
                : java.util.Optional.ofNullable(holds.get(Long.parseLong(query.replaceAll(".*transactionId=(\\d+).*", "$1"))))
                        .filter(h -> h.path("savingsId").asLong() == Long.parseLong(query.replaceAll(".*savingsId=(\\d+).*", "$1")));
        return hold.<Object[]>map(FakeCoreBanking::ok)
                .orElseGet(() -> new Object[] { 404, error(404, "error.msg.oneubank.hold.not.found", "No such hold") });
    }

    private Object[] virtualAccountRoute(String method, String api, JsonNode body, String idempotencyKey) {
        Matcher m;
        if (method.equals("POST") && api.equals("/oneubank/virtual-accounts")) {
            if (idempotencyKey != null && idempotent.containsKey(idempotencyKey)) {
                return ok(idempotent.get(idempotencyKey));
            }
            Object[] refused = unsupported(body, Set.of("parentAccountNumber", "type", "label", "accountNumber", "transactionReference"));
            if (refused != null) {
                return refused;
            }
            ObjectNode parent = savings.values().stream()
                    .filter(a -> a.path("accountNo").asText().equals(body.path("parentAccountNumber").asText())).findFirst().orElse(null);
            if (parent == null || !parent.path("status").path("active").asBoolean()) {
                return new Object[] { 403, error(403, "error.msg.oneubank.virtualaccount.parent.not.active", "Parent not active") };
            }
            String type = body.path("type").asText();
            Long productId = products.entrySet().stream().filter(p -> type.equals(p.getValue()[2])).map(Map.Entry::getKey)
                    .findFirst().orElse(null);
            if (productId == null) {
                return new Object[] { 403, error(403, "error.msg.oneubank.virtualaccount.type.unavailable", "No product for " + type) };
            }
            String number = body.path("accountNumber").asText(null);
            if (number != null && savings.values().stream().anyMatch(a -> number.equals(a.path("accountNo").asText()))) {
                return new Object[] { 403, error(403, "error.msg.savingsaccount.duplicate.accountNo", "Account number in use") };
            }
            long savingsId = ids.incrementAndGet();
            long clientId = parent.path("clientId").asLong();
            ObjectNode account = newAccount(savingsId, clientId, productId);
            if (number != null) {
                account.put("accountNo", number);
            }
            virtualAccounts.put(savingsId, JSON.createObjectNode().put("savingsId", savingsId)
                    .put("accountNumber", account.path("accountNo").asText())
                    .put("name", account.path("clientName").asText() + " - " + body.path("label").asText())
                    .put("label", body.path("label").asText()).put("type", type).put("parentSavingsId", parent.path("id").asLong())
                    .put("parentAccountNumber", parent.path("accountNo").asText()).put("clientId", clientId).put("status", "ACTIVE")
                    .put("transactionReference", body.path("transactionReference").asText(null))
                    .put("createdOn", "2026-11-23T09:00:00Z").put("updatedOn", "2026-11-23T09:00:00Z"));
            ObjectNode result = JSON.createObjectNode().put("savingsId", savingsId).put("clientId", clientId).put("resourceId", savingsId);
            if (idempotencyKey != null) {
                idempotent.put(idempotencyKey, result);
            }
            return ok(result);
        }
        if (method.equals("PUT") && (m = Pattern.compile("/oneubank/virtual-accounts/(\\d+)").matcher(api)).matches()) {
            ObjectNode va = virtualAccounts.get(Long.parseLong(m.group(1)));
            if ("CLOSED".equals(va.path("status").asText())) {
                return new Object[] { 403, error(403, "error.msg.oneubank.virtualaccount.closed", "Closed") };
            }
            String owner = va.path("name").asText().replaceFirst(" - .*$", "");
            va.put("label", body.path("label").asText()).put("name", owner + " - " + body.path("label").asText())
                    .put("updatedOn", "2026-11-23T10:00:00Z");
            return ok(JSON.createObjectNode().put("savingsId", va.path("savingsId").asLong()));
        }
        if (method.equals("POST") && (m = Pattern.compile("/oneubank/virtual-accounts/(\\d+)/close").matcher(api)).matches()) {
            long savingsId = Long.parseLong(m.group(1));
            ObjectNode va = virtualAccounts.get(savingsId);
            ObjectNode account = savings.get(savingsId);
            if (!"CLOSED".equals(va.path("status").asText())) {
                if (account.path("summary").path("accountBalance").decimalValue().signum() != 0) {
                    return new Object[] { 403, error(403, "error.msg.oneubank.virtualaccount.not.empty", "Not empty") };
                }
                boolean held = holds.values().stream().anyMatch(h -> h.path("savingsId").asLong() == savingsId && !h.path("released").asBoolean());
                JsonNode sub = account.path("subStatus");
                if (held || sub.path("block").asBoolean() || sub.path("blockDebit").asBoolean() || sub.path("blockCredit").asBoolean()) {
                    return new Object[] { 403, error(403, "error.msg.oneubank.virtualaccount.restricted", "Restricted") };
                }
                va.put("status", "CLOSED");
                ((ObjectNode) account.get("status")).put("active", false).put("closed", true);
            }
            return ok(JSON.createObjectNode().put("savingsId", savingsId));
        }
        if (method.equals("GET") && (m = Pattern.compile("/oneubank/virtual-accounts/(\\d+)").matcher(api)).matches()) {
            ObjectNode va = virtualAccounts.get(Long.parseLong(m.group(1)));
            return va == null ? new Object[] { 404, error(404, "error.msg.oneubank.virtualaccount.not.found", "No such VA") } : ok(va);
        }
        if (method.equals("GET") && api.equals("/oneubank/virtual-accounts")) {
            Map<String, String> q = new LinkedHashMap<>();
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    q.put(kv[0], kv[1]);
                }
            }
            if (q.containsKey("accountNumber") || q.containsKey("transactionReference")) {
                String field = q.containsKey("accountNumber") ? "accountNumber" : "transactionReference";
                return virtualAccounts.values().stream().filter(v -> q.get(field).equals(v.path(field).asText(null))).findFirst()
                        .<Object[]>map(FakeCoreBanking::ok)
                        .orElseGet(() -> new Object[] { 404, error(404, "error.msg.oneubank.virtualaccount.not.found", "No such VA") });
            }
            String owner = q.get("owner");
            long after = Long.parseLong(q.getOrDefault("afterSavingsId", "0"));
            int limit = Integer.parseInt(q.getOrDefault("limit", "20"));
            List<ObjectNode> matches = virtualAccounts.values().stream()
                    .filter(v -> v.path("savingsId").asLong() > after)
                    .filter(v -> owner.equals(datatables.getOrDefault("Customer Owner", Map.of()).get(v.path("clientId").asLong())
                            .get(0).path("Owner").asText()))
                    .filter(v -> !q.containsKey("parentSavingsId") || q.get("parentSavingsId").equals(v.path("parentSavingsId").asText()))
                    .filter(v -> !q.containsKey("type") || q.get("type").equals(v.path("type").asText())).toList();
            ObjectNode page = JSON.createObjectNode();
            ArrayNode items = page.putArray("items");
            matches.stream().limit(limit).forEach(items::add);
            page.put("hasMore", matches.size() > limit);
            return ok(page);
        }
        return new Object[] { 404, error(404, "error.msg.resource.not.found", "No fake for " + method + " " + api) };
    }

    /** A hold placed outside the Banking API (staff in the back office, or migrated from TP): no reference. */
    public synchronized long placeHoldWithoutReference(String accountNumber, String amount) {
        ObjectNode body = JSON.createObjectNode().put("transactionAmount", amount).put("reasonForBlock", "Placed by staff");
        holdAmount(savingsId(accountNumber), body);
        return holds.keySet().stream().reduce((a, b) -> b).orElseThrow();
    }

    private ObjectNode search(JsonNode body) {
        String owner = body.path("owner").asText();
        List<Long> matches = new ArrayList<>();
        for (ObjectNode client : clients.values()) {
            long id = client.path("id").asLong();
            ArrayNode ownerRows = datatables.getOrDefault("Customer Owner", Map.of()).get(id);
            if (id <= body.path("afterClientId").asLong(0) || ownerRows == null
                    || !owner.equals(ownerRows.get(0).path("Owner").asText())) {
                continue;
            }
            if (body.hasNonNull("bvn") && !hasIdentifier(id, "BVN", body.get("bvn").asText())
                    || body.hasNonNull("email") && !body.get("email").asText().equalsIgnoreCase(client.path("emailAddress").asText())
                    || body.hasNonNull("mobileNumber") && !body.get("mobileNumber").asText().equals(client.path("mobileNo").asText())) {
                continue;
            }
            matches.add(id);
        }
        int limit = body.path("limit").asInt(20);
        ObjectNode page = JSON.createObjectNode();
        ArrayNode ids = page.putArray("clientIds");
        matches.stream().limit(limit).forEach(ids::add);
        page.put("hasMore", matches.size() > limit);
        return page;
    }

    private boolean hasIdentifier(long clientId, String type, String key) {
        for (JsonNode i : identifiers.getOrDefault(clientId, JSON.createArrayNode())) {
            if (type.equals(i.path("documentType").path("name").asText()) && key.equals(i.path("documentKey").asText())) {
                return true;
            }
        }
        return false;
    }

    private static ObjectNode row(JsonNode data, long clientId) {
        ObjectNode row = JSON.createObjectNode().put("client_id", clientId);
        data.fields().forEachRemaining(field -> {
            if (!field.getKey().equals("locale") && !field.getKey().equals("dateFormat")) {
                JsonNode value = field.getValue();
                // Fineract stores decimals as numbers and returns them so.
                if (value.isTextual() && value.asText().matches("\\d+(\\.\\d+)?")
                        && (field.getKey().contains("Limit") || field.getKey().contains("Percentage"))) {
                    row.put(field.getKey(), new java.math.BigDecimal(value.asText()));
                } else {
                    row.set(field.getKey(), value);
                }
            }
        });
        return row;
    }

    private String name(String code, long id) {
        return codes.get(code).entrySet().stream().filter(e -> e.getValue() == id).map(Map.Entry::getKey).findFirst()
                .orElse(null);
    }

    private static Object[] ok(JsonNode body) {
        return new Object[] { 200, body };
    }

    private static ObjectNode error(int status, String code, String message) {
        ObjectNode error = JSON.createObjectNode().put("httpStatusCode", String.valueOf(status))
                .put("userMessageGlobalisationCode", code).put("defaultUserMessage", message)
                .put("developerMessage", message);
        error.putArray("errors");
        return error;
    }

    private static void send(HttpExchange exchange, int status, JsonNode body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
