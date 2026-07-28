package com.norcalretreat.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

/** Public payload for the "Request a Payment Plan" form. Deliberately
 *  narrower than {@link PaymentPlanDTO} so an anonymous submitter can't
 *  set status, tokens, Stripe IDs, or any admin-only field. */
@Data
public class PaymentPlanRequestDTO {
    /** Payer's full name (required). */
    private String payerName;
    /** Payer's email (required). Where the plan invite is sent once approved. */
    private String payerEmail;
    /** Optional phone -- routed into notes; not stored on the plan itself. */
    private String payerPhone;
    /** Which retreat this is for (required). Free text, e.g. "2027 Men's Retreat". */
    private String retreatLabel;
    /** Total the payer expects to pay across the plan (required, > 0). */
    private BigDecimal totalAmount;
    /** Payer's preferred number of installments (2-12). Stashed in notes so
     *  the admin can honor it when setting up the schedule. Optional. */
    private Integer preferredInstallments;
    /** Anything else the payer wants the admin to see (dietary, timing, etc). */
    private String message;
}
