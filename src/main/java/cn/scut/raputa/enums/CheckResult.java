package cn.scut.raputa.enums;

public enum CheckResult {
    DYSPHAGIA("吞咽障碍"),
    EXPLICIT_ASPIRATION("显性误吸"),
    SILENT_ASPIRATION("隐性误吸"),
    NORMAL("正常");

    private final String label;

    CheckResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static CheckResult fromLabel(String label) {
        for (CheckResult cr : values()) {
            if (cr.label.equals(label))
                return cr;
        }
        try {
            return CheckResult.valueOf(label);
        } catch (Exception ignored) {
        }
        return null;
    }
}
