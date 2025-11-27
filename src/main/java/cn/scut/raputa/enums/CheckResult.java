package cn.scut.raputa.enums;

public enum CheckResult {
    DYSPHAGIA("吞咽障碍"),
    ASPIRATION("误吸"),
    NORMAL("正常");

    private final String label;

    CheckResult(String label) { this.label = label; }

    public String getLabel() { return label; }

    public static CheckResult fromLabel(String label) {
        if (label == null) return null;
        switch (label.trim()) {
            case "误吸":
            case "显性误吸":
            case "隐性误吸":
                return ASPIRATION;
            case "吞咽障碍":
                return DYSPHAGIA;
            case "正常":
                return NORMAL;
        }
        try {
            return CheckResult.valueOf(label.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }
}
