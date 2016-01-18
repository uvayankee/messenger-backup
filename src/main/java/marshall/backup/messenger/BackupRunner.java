package marshall.backup.messenger;

import marshall.backup.messenger.webclient.FacebookClient;

/**
 * Created by Andrew on 1/9/2016.
 */
public class BackupRunner {

    public static void main(String[] args) {

        if(args.length != 4) {
            FacebookClient fbc = new FacebookClient("","","","");
            fbc.testFile();
            System.err.println("Params: username, password, messageIdQueryParam");
        } else {
            FacebookClient fbc = new FacebookClient(args[0], args[1], args[2], args[3]);
            fbc.start();
        }

    }


}
