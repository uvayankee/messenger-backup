package marshall.backup.messenger.model;

/**
 * Created by Andrew on 1/18/2016.
 *
 */
public class ImageData {

    public String getFullFileName() {
        return fullFileName;
    }

    public void setFullFileName(String fullFileName) {
        this.fullFileName = fullFileName;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    private String fullFileName;
    private byte[] image;
}
