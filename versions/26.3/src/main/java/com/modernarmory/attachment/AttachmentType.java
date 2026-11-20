package com.modernarmory.attachment;

/** Attachment slots, in the order the Gunsmith Table shows them. */
public enum AttachmentType {
    OPTIC("Optic"),
    MUZZLE("Muzzle"),
    UNDERBARREL("Underbarrel"),
    SIDE("Side Rail"),
    STOCK("Stock"),
    MAGAZINE("Magazine");

    private final String label;

    AttachmentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
