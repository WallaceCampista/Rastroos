package com.rastroos.web.form;

import com.rastroos.domain.entity.enums.SupportTicketCategory;
import com.rastroos.domain.entity.enums.SupportTicketPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Form de abertura de chamado de suporte. Categoria e prioridade são enums
 * (validados pela vinculação); título e descrição têm tamanho limitado.
 */
public class TicketForm {

    @NotNull
    private SupportTicketCategory category;

    @NotBlank
    @Size(min = 3, max = 200)
    private String title;

    @NotBlank
    @Size(min = 5, max = 4000)
    private String description;

    @NotNull
    private SupportTicketPriority priority;

    public TicketForm() {
    }

    public SupportTicketCategory getCategory() { return category; }
    public void setCategory(SupportTicketCategory category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SupportTicketPriority getPriority() { return priority; }
    public void setPriority(SupportTicketPriority priority) { this.priority = priority; }
}
