package com.oneutility.cba.bankingapi.corebanking.service;

import com.oneutility.cba.bankingapi.common.exception.ApiException;
import com.oneutility.cba.bankingapi.common.exception.ErrorCode;
import com.oneutility.cba.bankingapi.common.exception.FieldViolation;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreAddress;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreCustomer;
import com.oneutility.cba.bankingapi.corebanking.dto.CoreRelatedPerson;
import com.oneutility.cba.bankingapi.corebanking.dto.CreatedCustomer;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerChanges;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerIds;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerSearch;
import com.oneutility.cba.bankingapi.corebanking.dto.CustomerType;
import com.oneutility.cba.bankingapi.corebanking.dto.NewCustomer;
import com.oneutility.cba.bankingapi.corebanking.dto.RelatedPersonRole;
import com.oneutility.cba.bankingapi.corebanking.dto.RiskRating;
import com.oneutility.cba.bankingapi.corebanking.fineract.AddressFields;
import com.oneutility.cba.bankingapi.corebanking.fineract.ClientAddressResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.ClientChanges;
import com.oneutility.cba.bankingapi.corebanking.fineract.ClientIdentifierResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.ClientResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.CommandResult;
import com.oneutility.cba.bankingapi.corebanking.fineract.CreateCustomerCommand;
import com.oneutility.cba.bankingapi.corebanking.fineract.CustomerSearchQuery;
import com.oneutility.cba.bankingapi.corebanking.fineract.CustomerSearchResult;
import com.oneutility.cba.bankingapi.corebanking.fineract.DatatableEntry;
import com.oneutility.cba.bankingapi.corebanking.fineract.DatatableRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.DatatableRows;
import com.oneutility.cba.bankingapi.corebanking.fineract.FineractLocale;
import com.oneutility.cba.bankingapi.corebanking.fineract.IdentifierFields;
import com.oneutility.cba.bankingapi.corebanking.fineract.KycRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.LimitRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.NewClient;
import com.oneutility.cba.bankingapi.corebanking.fineract.OwnerRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.RelatedPersonRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.RiskRow;
import com.oneutility.cba.bankingapi.corebanking.fineract.UpdateCustomerCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Customers in CBA-CORE: the one place that knows how 1UBank's customer data is stored there (Fineract client fields,
 * identifiers, the address module, the customer datatables and their dropdown lists). Callers deal only in this
 * module's records.
 *
 * <p>
 * Customers belong to an API client (the {@code Customer Owner} datatable); a customer of another client is treated
 * as not found.
 */
@Service
public class CoreBankingCustomers {

    // Dropdown lists (Fineract codes) and their values, as seeded in CBA-CORE
    static final String IDENTIFIER_TYPES = "Customer Identifier";
    static final String BVN = "BVN";
    static final String NIN = "NIN";
    static final String CAC = "CAC Registration Number";
    static final String TIN = "TIN";
    static final String ADDRESS_TYPES = "ADDRESS_TYPE";
    static final String STATES = "STATE";
    static final String COUNTRIES = "COUNTRY";
    static final String DEFAULT_COUNTRY = "Nigeria";
    static final String KYC_LEVELS = "KycLevel";
    static final String RISK_RATINGS = "RiskRating";
    static final String ROLES = "RelatedPersonRole";

    static final ZoneId BANK_ZONE = ZoneId.of("Africa/Lagos");
    static final int LEGAL_FORM_PERSON = 1;
    static final int LEGAL_FORM_BUSINESS = 2;

    private final FineractApi api;
    private final CodeValues codeValues;
    private final CoreBankingProperties properties;

    CoreBankingCustomers(FineractApi api, CodeValues codeValues, CoreBankingProperties properties) {
        this.api = api;
        this.codeValues = codeValues;
        this.properties = properties;
    }

    /**
     * Creates the customer, their identifiers and their first account (opened and active) in one CBA-CORE
     * transaction: all of it, or nothing.
     *
     * @throws ApiException {@code VALIDATION_FAILED} for a value not in CBA-CORE's lists (state, country, KYC level)
     * @throws CoreBankingException when CBA-CORE refuses, for example a BVN or phone number already in use
     */
    public CreatedCustomer create(NewCustomer customer) {
        boolean business = customer.type() == CustomerType.BUSINESS;
        List<DatatableEntry> datatables = new ArrayList<>();
        datatables.add(new DatatableEntry(OwnerRow.TABLE, new OwnerRow(customer.owner())));
        datatables.add(new DatatableEntry(KycRow.TABLE, KycRow.of(kycLevelId(customer.kycLevel(), "kycLevel"))));
        if (customer.pep() != null || customer.riskRating() != null) {
            datatables.add(new DatatableEntry(RiskRow.TABLE, riskRow(customer.pep(), customer.riskRating())));
        }
        if (customer.transactionLimit() != null) {
            datatables.add(new DatatableEntry(LimitRow.TABLE, LimitRow.of(customer.transactionLimit())));
        }
        List<RelatedPersonRow> related = relatedPersonRows(customer.relatedPersons());
        related.forEach(row -> datatables.add(new DatatableEntry(RelatedPersonRow.TABLE, row)));

        String today = LocalDate.now(BANK_ZONE).toString();
        NewClient client = new NewClient(properties.officeId(), business ? LEGAL_FORM_BUSINESS : LEGAL_FORM_PERSON,
                business ? null : customer.firstName(), business ? null : customer.middleName(),
                business ? null : customer.lastName(), business ? customer.fullName() : null,
                date(customer.dateOfBirth()), customer.email(), customer.mobileNumber(), true, today, today,
                customer.productId(), List.of(address(customer.address(), customer.type())), datatables,
                FineractLocale.ENGLISH, FineractLocale.DATE_FORMAT);

        CommandResult created = api.createCustomer(new CreateCustomerCommand(client,
                identifiers(customer.bvn(), customer.nin(), customer.cacNumber(), customer.tin())), customer.idempotencyKey());
        return new CreatedCustomer(created.clientId(), created.savingsId());
    }

    /**
     * Updates the customer in one CBA-CORE transaction: every change, or none. The caller checks the customer belongs
     * to it first.
     *
     * @throws ApiException {@code VALIDATION_FAILED} for a value not in CBA-CORE's lists (state, country, role)
     * @throws CoreBankingException when CBA-CORE refuses, for example a NIN or phone number already in use
     */
    public void update(long clientId, CustomerType type, CustomerChanges changes) {
        ClientChanges client = new ClientChanges(changes.firstName(), changes.middleName(), changes.lastName(),
                changes.fullName(), date(changes.dateOfBirth()), changes.email(), changes.mobileNumber(),
                FineractLocale.ENGLISH, FineractLocale.DATE_FORMAT);
        UpdateCustomerCommand command = new UpdateCustomerCommand(client.isEmpty() ? null : client,
                identifiers(changes.bvn(), changes.nin(), changes.cacNumber(), changes.tin()),
                changes.address() == null ? null : address(changes.address(), type),
                changes.relatedPersons() == null ? null
                        : List.of(new DatatableRows(RelatedPersonRow.TABLE, relatedPersonRows(changes.relatedPersons()))));
        if (!command.isEmpty()) {
            api.updateCustomer(clientId, command);
        }
    }

    /** The customer, if they exist and belong to {@code owner}. */
    public Optional<CoreCustomer> find(long clientId, String owner) {
        Optional<String> recordedOwner = owner(clientId);
        if (recordedOwner.isEmpty() || !recordedOwner.get().equals(owner)) {
            return Optional.empty();
        }
        ClientResponse client = api.getClient(clientId);
        CustomerType type = client.legalForm() != null && client.legalForm().id() != null
                && client.legalForm().id() == LEGAL_FORM_BUSINESS ? CustomerType.BUSINESS : CustomerType.PERSON;

        Map<String, String> identifiers = new LinkedHashMap<>();
        for (ClientIdentifierResponse identifier : api.getIdentifiers(clientId)) {
            if (isActive(identifier) && identifier.documentType() != null) {
                identifiers.put(identifier.documentType().name(), identifier.documentKey());
            }
        }

        KycRow kyc = first(api.getKycRows(KycRow.TABLE, clientId));
        RiskRow risk = first(api.getRiskRows(RiskRow.TABLE, clientId));
        LimitRow limit = first(api.getLimitRows(LimitRow.TABLE, clientId));
        List<CoreRelatedPerson> related = type == CustomerType.BUSINESS
                ? api.getRelatedPersonRows(RelatedPersonRow.TABLE, clientId).stream().map(this::relatedPerson).toList()
                : List.of();

        return Optional.of(new CoreCustomer(clientId, recordedOwner.get(), type, blankToNull(client.firstname()),
                blankToNull(client.middlename()), blankToNull(client.lastname()), blankToNull(client.fullname()),
                client.dateOfBirth(), blankToNull(client.emailAddress()), blankToNull(client.mobileNo()),
                identifiers.get(BVN), identifiers.get(NIN), identifiers.get(CAC), identifiers.get(TIN), address(clientId),
                kyc == null ? null : codeValues.name(KYC_LEVELS, kyc.kycLevelId()).map(Integer::valueOf).orElse(null),
                risk == null ? null : risk.pep(),
                risk == null ? null
                        : codeValues.name(RISK_RATINGS, risk.riskRatingId())
                                .map(name -> RiskRating.valueOf(name.toUpperCase(Locale.ROOT))).orElse(null),
                limit == null ? null : limit.transactionLimit(), related, status(client)));
    }

    /** The customer's KYC level, if one is recorded. */
    public Optional<Integer> kycLevel(long clientId) {
        return Optional.ofNullable(first(api.getKycRows(KycRow.TABLE, clientId)))
                .flatMap(kyc -> codeValues.name(KYC_LEVELS, kyc.kycLevelId())).map(Integer::valueOf);
    }

    /** One page of {@code owner}'s customers matching the search. */
    public CustomerIds search(CustomerSearch search) {
        CustomerSearchResult page = api.searchCustomers(new CustomerSearchQuery(search.owner(), search.bvn(),
                search.nin(), search.email(), search.mobileNumber(), search.afterClientId(), search.limit()));
        return new CustomerIds(page.clientIds(), page.hasMore());
    }

    /** The API client a customer belongs to, if one is recorded. */
    public Optional<String> owner(long clientId) {
        try {
            return Optional.ofNullable(first(api.getOwnerRows(OwnerRow.TABLE, clientId))).map(OwnerRow::owner);
        } catch (CoreBankingException e) {
            if (e.isNotFound()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** Sets the customer's KYC level. Check ownership first. */
    public void setKycLevel(long clientId, int kycLevel) {
        upsert(KycRow.TABLE, () -> api.getKycRows(KycRow.TABLE, clientId), clientId, KycRow.of(kycLevelId(kycLevel, "kycLevel")));
    }

    /** Sets PEP and/or risk rating; a {@code null} leaves that value as it is. Check ownership first. */
    public void setRiskProfile(long clientId, Boolean pep, RiskRating riskRating) {
        upsert(RiskRow.TABLE, () -> api.getRiskRows(RiskRow.TABLE, clientId), clientId, riskRow(pep, riskRating));
    }

    /** Sets the customer's own transaction limit. Check ownership first. */
    public void setTransactionLimit(long clientId, BigDecimal transactionLimit) {
        upsert(LimitRow.TABLE, () -> api.getLimitRows(LimitRow.TABLE, clientId), clientId, LimitRow.of(transactionLimit));
    }

    // Writing

    private void upsert(String table, Supplier<List<? extends DatatableRow>> existing, long clientId, DatatableRow row) {
        if (existing.get().isEmpty()) {
            api.createDatatableRow(table, clientId, row);
        } else {
            api.updateDatatableRow(table, clientId, row);
        }
    }

    private RiskRow riskRow(Boolean pep, RiskRating riskRating) {
        return RiskRow.of(pep, riskRating == null ? null
                : required(RISK_RATINGS, capitalised(riskRating.name()), "riskRating"));
    }

    private List<RelatedPersonRow> relatedPersonRows(List<CoreRelatedPerson> persons) {
        List<RelatedPersonRow> rows = new ArrayList<>();
        for (int i = 0; i < persons.size(); i++) {
            CoreRelatedPerson p = persons.get(i);
            rows.add(RelatedPersonRow.of(p.firstName(), p.middleName(), p.lastName(),
                    required(ROLES, roleName(p.role()), "relatedPersons[" + i + "].role"), p.bvn(), p.nin(),
                    p.dateOfBirth(), p.mobileNumber(), p.email(), p.address(),
                    p.ownershipPercentage() == null ? null : p.ownershipPercentage().stripTrailingZeros()));
        }
        return rows;
    }

    private AddressFields address(CoreAddress address, CustomerType type) {
        return new AddressFields(
                required(ADDRESS_TYPES, type == CustomerType.BUSINESS ? "Business" : "Residential", "address"),
                address.addressLine1(), address.addressLine2(), address.addressLine3(), address.street(),
                address.townVillage(), address.city(), address.district(),
                address.state() == null ? null : required(STATES, address.state(), "address.state"),
                required(COUNTRIES, address.country() == null ? DEFAULT_COUNTRY : address.country(), "address.country"),
                address.postalCode(), true);
    }

    private List<IdentifierFields> identifiers(String bvn, String nin, String cacNumber, String tin) {
        List<IdentifierFields> identifiers = new ArrayList<>();
        addIdentifier(identifiers, BVN, bvn);
        addIdentifier(identifiers, NIN, nin);
        addIdentifier(identifiers, CAC, cacNumber);
        addIdentifier(identifiers, TIN, tin);
        return identifiers;
    }

    private void addIdentifier(List<IdentifierFields> identifiers, String type, String value) {
        if (value != null) {
            identifiers.add(IdentifierFields.active(required(IDENTIFIER_TYPES, type, type.toLowerCase(Locale.ROOT)),
                    value));
        }
    }

    private long kycLevelId(int kycLevel, String field) {
        return codeValues.id(KYC_LEVELS, Integer.toString(kycLevel)).orElseThrow(() -> invalid(field,
                "No KYC level " + kycLevel + " is configured"));
    }

    private long required(String code, String name, String field) {
        return codeValues.id(code, name).orElseThrow(() -> invalid(field, "'" + name + "' is not a known value"));
    }

    // Reading

    /** The customer's active address; the newest if there are several. */
    private CoreAddress address(long clientId) {
        return api.getAddresses(clientId).stream().filter(a -> !Boolean.FALSE.equals(a.isActive()))
                .max(Comparator.comparingLong(ClientAddressResponse::addressId))
                .map(a -> new CoreAddress(blankToNull(a.addressLine1()), blankToNull(a.addressLine2()),
                        blankToNull(a.addressLine3()), blankToNull(a.street()), blankToNull(a.townVillage()),
                        blankToNull(a.city()), blankToNull(a.countyDistrict()), blankToNull(a.stateName()),
                        blankToNull(a.countryName()), blankToNull(a.postalCode())))
                .orElse(null);
    }

    private CoreRelatedPerson relatedPerson(RelatedPersonRow row) {
        RelatedPersonRole role = codeValues.name(ROLES, row.roleId()).map(CoreBankingCustomers::role).orElse(null);
        return new CoreRelatedPerson(blankToNull(row.firstName()), blankToNull(row.middleName()),
                blankToNull(row.lastName()), role, blankToNull(row.bvn()), blankToNull(row.nin()), row.dateOfBirth(),
                blankToNull(row.phone()), blankToNull(row.email()), blankToNull(row.address()),
                row.ownershipPercentage() == null ? null : row.ownershipPercentage().stripTrailingZeros());
    }

    private static boolean isActive(ClientIdentifierResponse identifier) {
        String status = identifier.status() == null ? "" : identifier.status().toLowerCase(Locale.ROOT);
        return status.isEmpty() || status.contains("active") && !status.contains("inactive");
    }

    private static String status(ClientResponse client) {
        String code = client.status() == null || client.status().code() == null ? "" : client.status().code();
        if (code.endsWith(".active")) {
            return "ACTIVE";
        }
        if (code.endsWith(".closed") || code.endsWith(".rejected") || code.endsWith(".withdrawn")) {
            return "CLOSED";
        }
        return "INACTIVE";
    }

    // Helpers

    static String roleName(RelatedPersonRole role) {
        return switch (role) {
            case DIRECTOR -> "Director";
            case SIGNATORY -> "Signatory";
            case DIRECTOR_AND_SIGNATORY -> "Director & Signatory";
        };
    }

    static RelatedPersonRole role(String name) {
        return switch (name) {
            case "Director" -> RelatedPersonRole.DIRECTOR;
            case "Signatory" -> RelatedPersonRole.SIGNATORY;
            case "Director & Signatory" -> RelatedPersonRole.DIRECTOR_AND_SIGNATORY;
            default -> null;
        };
    }

    private static <T> T first(List<T> rows) {
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private static String capitalised(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String date(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message, List.of(new FieldViolation(field, message)));
    }
}
