package marshall.backup.messenger;

/**
 * Created by Andrew on 1/11/2016.
 */
public class Utils {
    public static String fixAmpersands(String url) {
        return url.replace("&amp;", "&");
    }
}
