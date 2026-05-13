package cn.scut.raputa.enums;

public enum CheckResult {
    NORMAL("正常"),
    DYSPHAGIA("吞咽障碍"),
    OVERT_ASPIRATION("误吸"),
    SILENT_ASPIRATION("误吸"),
    ASPIRATION("误吸");

    private final String label;

    CheckResult(String label) { this.label = label; }

    public String getLabel() { return label; }

    public static CheckResult fromLabel(String label) {
        if (label == null) return null;
        switch (label.trim()) {
            case "正常":
                return NORMAL;
            case "吞咽障碍":
                return DYSPHAGIA;
            case "误吸":
                return ASPIRATION;
        }
        try {
            return CheckResult.valueOf(label.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean isAspiration() {
        return this == ASPIRATION || this == OVERT_ASPIRATION || this == SILENT_ASPIRATION;
    }
}
