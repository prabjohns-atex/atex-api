package com.atex.onecms.app.dam.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.atex.onecms.app.dam.solr.SolrPrintPageService;

public class QueryField {

    private final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd");

    private String key;
    private String name;
    private String type;
    private String value;
    private List<String> values = new ArrayList<>();
    private boolean operator = false;
    private boolean subQuery = false;
    private boolean useId = false;
    private boolean useValue = false;
    private boolean useBoolean = false;
    private boolean split = false;
    private List<String> model = new ArrayList<>();
    private List<String> extraValues = new ArrayList<>();
    private boolean ignore = false;
    private boolean fulltext = false;
    private String queryOperator;
    private String disablePatchDate;
    private String offsetDateInterval;

    public QueryField() {}
    public QueryField(String name, String value) { this.name = name; this.key = name; this.value = value; }

    public String getKey() { return key != null ? key : name; }
    public void setKey(String key) { this.key = key; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getValue() { return value; }
    public void setValue(String v) { this.value = v; }
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
    public List<String> getExtraValues() { return extraValues; }
    public void setExtraValues(List<String> extraValues) { this.extraValues = extraValues; }
    public boolean isSubQuery() { return subQuery; }
    public void setSubQuery(boolean subQuery) { this.subQuery = subQuery; }
    public boolean isUseId() { return useId; }
    public void setUseId(boolean useId) { this.useId = useId; }
    public boolean isUseValue() { return useValue; }
    public void setUseValue(boolean useValue) { this.useValue = useValue; }
    public boolean isUseBoolean() { return useBoolean; }
    public void setUseBoolean(boolean useBoolean) { this.useBoolean = useBoolean; }
    public String getQueryOperator() { return queryOperator; }
    public void setQueryOperator(String queryOperator) { this.queryOperator = queryOperator; }
    public String getDisablePatchDate() { return disablePatchDate; }
    public void setDisablePatchDate(String disablePatchDate) { this.disablePatchDate = disablePatchDate; }
    public String getOffsetDateInterval() { return offsetDateInterval; }
    public void setOffsetDateInterval(String offsetDateInterval) { this.offsetDateInterval = offsetDateInterval; }
    public boolean isSplit() { return split; }
    public void setSplit(boolean split) { this.split = split; }
    public boolean isOperator() { return operator; }
    public void setOperator(boolean operator) { this.operator = operator; }
    public List<String> getModel() { return model; }
    public void setModel(List<String> model) { this.model = model; }
    public boolean isIgnore() { return ignore; }
    public void setIgnore(boolean ignore) { this.ignore = ignore; }
    public boolean isFulltext() { return fulltext; }
    public void setFulltext(boolean fulltext) { this.fulltext = fulltext; }

    public String translate() {
        return (this.operator) ? "-" : "+";
    }

    public String processSubQuery(SolrPrintPageService service) {
        String processed = "";

        if (notNull(this.values) && !this.ignore) {
            if (type == null) return processed;
            switch (this.type) {
                case "LIST": {
                    List<String> _values = parse();
                    processed = translate() + getKey() + ":(\\\"" + String.join("\\\" \\\"", _values) + "\\\")";
                    break;
                }
                case "BOOLEAN": {
                    boolean include = Boolean.parseBoolean(values.get(0));
                    if (include) {
                        processed = "+" + getKey() + ":(*)";
                    }
                    break;
                }
                case "STRING": {
                    String val = this.values.get(0);
                    if (val != null && !val.isEmpty()) {
                        if (this.fulltext) {
                            processed = translate() + getKey() + ":(" + val + ")";
                            break;
                        }
                        if (val.indexOf(",") > 0) {
                            String[] vals = val.split(",");
                            processed = translate() + getKey() + ":(" + String.join(" ", vals) + ")";
                        } else if (val.indexOf("-") > 0) {
                            String[] vals = val.split("-");
                            processed = translate() + getKey() + ":[" + String.join(" TO ", vals) + "]";
                        } else {
                            processed = translate() + getKey() + ":(" + val + ")";
                        }
                    }
                    break;
                }
                case "GEOLOCATION": {
                    processed = processGeoLocation();
                    break;
                }
                case "DATE": {
                    processed = processDate();
                    break;
                }
                case "ENGAGE": {
                    processed = processEngage();
                    break;
                }
                case "PRINT-DATE": {
                    // Print date processing requires SolrPrintPageService — stub for now
                    break;
                }
            }

            if (notNull(this.extraValues)) {
                processed += "+" + getKey() + ":(\\\"" + String.join("\\\" \\\"", this.extraValues) + "\\\")";
            }
        }

        return processed;
    }

    public String processQuery(SolrPrintPageService service) {
        String processed = "";

        if (notNull(this.values) && !this.ignore) {
            if (type == null) return processed;
            switch (this.type) {
                case "STRING": {
                    String val = this.values.get(0);
                    if (val != null && !val.isEmpty()) {
                        if (this.fulltext) {
                            processed = translate() + getKey() + ":(" + val + ")";
                            break;
                        }
                        if ((val.startsWith("\"") && val.endsWith("\"")) ||
                                (val.startsWith("'") && val.endsWith("'"))) {
                            processed = translate() + getKey() + ":(" + val + ")";
                        } else if (val.indexOf(",") > 0) {
                            String[] vals = val.split(",");
                            processed = translate() + getKey() + ":(" + String.join(" ", vals) + ")";
                        } else if (val.indexOf("-") > 0) {
                            String[] vals = val.split("-");
                            processed = translate() + getKey() + ":[" + String.join(" TO ", vals) + "]";
                        } else {
                            processed = translate() + getKey() + ":(" + val + ")";
                        }
                    }
                    break;
                }
                case "LIST": {
                    List<String> _values = parse();
                    processed = translate() + getKey() + ":(\"" + String.join("\" \"", _values) + "\")";
                    break;
                }
                case "BOOLEAN": {
                    boolean checked = Boolean.parseBoolean(values.get(0));
                    if (useValue) {
                        processed = (checked ? "+" : "-") + getKey() + ":" + "(\"true\")";
                    } else if (useBoolean && checked) {
                        processed = "+" + getKey() + ":" + "(\"true\")";
                    } else if (checked) {
                        processed = "+" + getKey() + ":(*)";
                    }
                    break;
                }
                case "DATE": {
                    processed = processDate();
                    break;
                }
                case "ENGAGE": {
                    processed = processEngage();
                    break;
                }
                case "NUM-RANGE": {
                    processed = processNumRange();
                    break;
                }
            }
        }

        if (notNull(this.extraValues)) {
            processed += "+" + getKey() + ":(\"" + String.join("\" \"", this.extraValues) + "\")";
        }

        return processed;
    }

    private String processEngage() {
        String processed = "";
        if (notNull(this.values)) {
            if (this.values.size() == 1 && this.values.get(0).equals("-*")) {
                processed = "-" + getKey() + ":(*)";
            } else if (this.values.size() == 1 && this.values.get(0).startsWith("-") && this.values.get(0).length() > 1 && Character.isLetter(this.values.get(0).charAt(1))) {
                processed = "-" + getKey() + ":(" + this.values.get(0).replace("-","") + ")";
            } else if (this.values.size() == 1 && this.values.get(0).equals("*")) {
                processed = "+" + getKey() + ":(*)";
            } else {
                processed = "+" + getKey() + ":(\"" + String.join("\" \"", this.values) + "\")";
            }
        }
        return processed;
    }

    private String processNumRange() {
        String processed = "";
        if (notNull(this.values)) {
            if (this.values.size() >= 2) {
                boolean close = true;
                String fromOp = this.values.get(0).trim();
                String fromValue = this.values.get(1).trim();
                switch (fromOp) {
                    case "=":
                        processed = "(" + fromValue + ")";
                        close = false;
                        break;
                    case ">=": case "=>":
                        processed = "[" + fromValue + " TO ";
                        break;
                    case ">":
                        processed = "{" + fromValue + " TO ";
                        break;
                    case "<":
                        processed = "[* TO " + fromValue + "}";
                        close = false;
                        break;
                    case "<=": case "=<":
                        processed = "[* TO " + fromValue + "]";
                        close = false;
                        break;
                }
                if (close && this.values.size() >= 4) {
                    String toOp = this.values.get(2).trim();
                    String toValue = this.values.get(3).trim();
                    switch (toOp) {
                        case "<=": case "=<":
                            processed += toValue + "]";
                            break;
                        case "<":
                            processed += toValue + "}";
                            break;
                    }
                    close = false;
                }
                if (close) {
                    processed += "*]";
                }
            }
        }
        if (processed != null && !processed.isEmpty()) {
            return "+" + getKey() + ":" + processed;
        }
        return processed;
    }

    private String processDate() {
        String processed = "";
        if (notNull(this.values)) {
            // Handle offset date interval
            if (this.offsetDateInterval != null && !this.offsetDateInterval.isEmpty()) {
                try {
                    String[] parts = this.offsetDateInterval.split(";");
                    if (parts.length == 2) {
                        SimpleDateFormat sdf = new SimpleDateFormat(parts[1]);
                        if (this.values.size() >= 1 && !this.values.get(0).contains("NOW")) {
                            Date dateStart = sdf.parse(this.values.get(0));
                            this.values.set(0, sdf.format(new Date(dateStart.getTime() + Integer.parseInt(parts[0]))));
                        }
                        if (this.values.size() == 2 && !this.values.get(1).contains("NOW")) {
                            Date dateEnd = sdf.parse(this.values.get(1));
                            this.values.set(1, sdf.format(new Date(dateEnd.getTime() + Integer.parseInt(parts[0]))));
                        }
                    }
                } catch (ParseException e) {
                    // Skip offset if date parsing fails
                }
            }

            if (this.values.size() == 2) {
                if (this.values.get(0) == null || this.values.get(0).isEmpty()) {
                    this.values.set(0, "*");
                }
                if (this.values.get(1) == null || this.values.get(1).isEmpty()) {
                    this.values.set(1, "*");
                }
                if (!this.values.get(0).trim().equals("*") || !this.values.get(1).trim().equals("*")) {
                    processed = "+" + getKey() + ":[" + String.join(" TO ", this.values) + "]";
                }
            } else if (this.values.size() == 1) {
                processed = "+" + getKey() + ":[" + this.values.get(0) + "]";
            }
        }
        return processed;
    }

    private String processGeoLocation() {
        String processed = "";
        if (notNull(this.values)) {
            for (int i = 0; i < this.values.size(); i++) {
                String val = this.values.get(i);
                String[] arr = val.split(",");
                if (arr.length > 2) {
                    if (i == 0) {
                        processed = "+(";
                    } else {
                        processed += " OR ";
                    }
                    processed += "_query_:{!geofilt%20sfield=" + getKey() + "%20pt=" + arr[0] + "," + arr[1] + "%20d=" + arr[2] + "}";
                    if (this.values.size() - 1 == i) {
                        processed += ")";
                    }
                }
            }
        }
        return processed;
    }

    private boolean notNull(List<?> list) {
        return list != null && !list.isEmpty();
    }

    private List<String> parse() {
        List<String> _values = new ArrayList<>();
        if (this.split) {
            for (String val : this.values) {
                if (val.contains("/")) {
                    String[] splitted = val.split("/");
                    _values.add(splitted[splitted.length - 1]);
                } else {
                    _values.add(val);
                }
            }
        } else {
            _values = this.values;
        }
        return _values;
    }
}
