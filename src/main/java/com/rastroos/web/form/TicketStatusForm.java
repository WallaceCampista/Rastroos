package com.rastroos.web.form;

import com.rastroos.domain.entity.enums.SupportTicketStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Form usado pelo admin para mudar o status de um chamado.
 */
public class TicketStatusForm {

    @NotNull
    private SupportTicketStatus status;

    public TicketStatusForm() {
    }

    public SupportTicketStatus getStatus() { return status; }
    public void setStatus(SupportTicketStatus status) { this.status = status; }
}
