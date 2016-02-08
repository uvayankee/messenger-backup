package marshall.backup.messenger;

import marshall.backup.messenger.webclient.FacebookClient;

/**
 * Command Line runner for FB Client
 */
public class BackupRunner {

    public static void main(String[] args) {

        if(args.length != 4) {
            System.err.println("Params: username, password, messageIdQueryParam, outputDirectory");
        } else {
            FacebookClient fbc = new FacebookClient(args[0], args[1], args[2], args[3]);
            fbc.start();
        }

    }


}
