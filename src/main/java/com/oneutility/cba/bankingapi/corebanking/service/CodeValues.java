package com.oneutility.cba.bankingapi.corebanking.service;

import com.oneutility.cba.bankingapi.corebanking.fineract.CodeResponse;
import com.oneutility.cba.bankingapi.corebanking.fineract.CodeValueResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * CBA-CORE's dropdown lists (Fineract codes), by name: for example {@code STATE} → {@code Lagos} → 41. Read on first
 * use and kept for {@link #TTL}; a name not found is looked up again at once, so values staff add in the portal are
 * picked up without waiting.
 */
@Component
class CodeValues {

    static final Duration TTL = Duration.ofMinutes(10);

    private final FineractApi api;
    private final Clock clock;
    private final Map<String, Code> codes = new ConcurrentHashMap<>();

    @Autowired
    CodeValues(FineractApi api) {
        this(api, Clock.systemUTC());
    }

    CodeValues(FineractApi api, Clock clock) {
        this.api = api;
        this.clock = clock;
    }

    /** The ID of value {@code name} (any case) in list {@code code}, if there is one. */
    Optional<Long> id(String code, String name) {
        if (name == null) {
            return Optional.empty();
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        Long id = load(code, false).byName.get(key);
        if (id == null) {
            id = load(code, true).byName.get(key);
        }
        return Optional.ofNullable(id);
    }

    /** The name of value {@code id} in list {@code code}, if there is one. */
    Optional<String> name(String code, Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String name = load(code, false).byId.get(id);
        if (name == null) {
            name = load(code, true).byId.get(id);
        }
        return Optional.ofNullable(name);
    }

    private Code load(String code, boolean refresh) {
        Code cached = codes.get(code);
        if (cached != null && !refresh && clock.instant().isBefore(cached.loadedAt.plus(TTL))) {
            return cached;
        }
        Long codeId = null;
        for (CodeResponse c : api.getCodes()) {
            if (code.equals(c.name())) {
                codeId = c.id();
            }
        }
        if (codeId == null) {
            throw new IllegalStateException("CBA-CORE has no code list " + code + "; is its customer data seeded?");
        }
        Map<String, Long> byName = new HashMap<>();
        Map<Long, String> byId = new HashMap<>();
        for (CodeValueResponse v : api.getCodeValues(codeId)) {
            if (!Boolean.FALSE.equals(v.active())) {
                byName.put(v.name().trim().toLowerCase(Locale.ROOT), v.id());
            }
            byId.put(v.id(), v.name());
        }
        Code loaded = new Code(Map.copyOf(byName), Map.copyOf(byId), clock.instant());
        codes.put(code, loaded);
        return loaded;
    }

    private record Code(Map<String, Long> byName, Map<Long, String> byId, Instant loadedAt) {
    }
}
