package cn.scut.raputa.enums;

public enum CheckResult {
    NORMAL("正常"),
    DYSPHAGIA("吞咽障碍"),
    OVERT_ASPIRATION("显性误吸"),
    SILENT_ASPIRATION("隐性误吸"),
    ASPIRATION("误吸");  // 保留用于向后兼容

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
            case "显性误吸":
                return OVERT_ASPIRATION;
            case "隐性误吸":
                return SILENT_ASPIRATION;
            case "误吸":
                return ASPIRATION;
        }
        try {
            return CheckResult.valueOf(label.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }
}
