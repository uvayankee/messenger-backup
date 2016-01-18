package marshall.backup.messenger.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Andrew on 1/9/2016.
 */
public class FileData implements Comparable<FileData> {
    private Date sendDate;
    private String message;
    private String Sender;
    private List<String> attachedImages;

    public FileData() {
        attachedImages = new ArrayList<String>();
    }

    @Override
    public int compareTo(FileData o) {
        return this.getSendDate().compareTo(o.getSendDate());
    }

    public Date getSendDate() {
        return sendDate;
    }

    public void setSendDate(Date sendDate) {
        this.sendDate = sendDate;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSender() {
        return Sender;
    }

    public void setSender(String sender) {
        Sender = sender;
    }

    public boolean hasAttachedImage() {
        return !attachedImages.isEmpty();
    }

    public void addAttachedImage(String imageFileName) {
        attachedImages.add(imageFileName);
    }

    public List<String> getAttachedImages() {
        return attachedImages;
    }
}
