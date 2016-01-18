package marshall.backup.messenger.model;

import java.util.Date;

/**
 * Created by Andrew on 1/17/2016.
 */
public class DataStore {

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String name = "";

    public boolean hasAttachment() {
        return (has_attachment != null && has_attachment.equals("true"));
    }

    public void setHas_attachment(String has_attachment) {
        this.has_attachment = has_attachment;
    }

    private String has_attachment;

    private String timestamp;

    public Date getTimestamp() {
        String tempTimestamp = timestamp.substring(0,timestamp.length() -3);
        long lTimestamp = Long.parseLong(timestamp);
        return new Date(lTimestamp);
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getAuthor() {
        return Author;
    }

    public void setAuthor(String author) {
        Author = author;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    private String Author;
    private String uuid;
    public DataStore(){
    }
}
