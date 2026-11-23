package com.oneutility.cba.bankingapi.corebanking.fineract;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code Related Persons}: a business's directors and signatories, one row each; the role is code list {@code RelatedPersonRole}. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelatedPersonRow(@JsonProperty("First Name") String firstName,
        @JsonProperty("Middle Name") String middleName, @JsonProperty("Last Name") String lastName,
        @JsonProperty("RelatedPersonRole_cd_Role") Long roleId, @JsonProperty("BVN") String bvn,
        @JsonProperty("NIN") String nin,
        @JsonProperty("Date of Birth") @JsonDeserialize(using = FineractDates.class) @JsonFormat(pattern = FineractLocale.DATE_FORMAT) LocalDate dateOfBirth,
        @JsonProperty("Phone") String phone, @JsonProperty("Email") String email,
        @JsonProperty("Address") String address,
        @JsonProperty("Ownership Percentage") @JsonSerialize(using = PlainDecimal.class) BigDecimal ownershipPercentage,
        String locale, String dateFormat) implements DatatableRow {

    public static final String TABLE = "Related Persons";

    public static RelatedPersonRow of(String firstName, String middleName, String lastName, long roleId, String bvn,
            String nin, LocalDate dateOfBirth, String phone, String email, String address, BigDecimal ownershipPercentage) {
        return new RelatedPersonRow(firstName, middleName, lastName, roleId, bvn, nin, dateOfBirth, phone, email,
                address, ownershipPercentage, FineractLocale.ENGLISH, FineractLocale.DATE_FORMAT);
    }
}
