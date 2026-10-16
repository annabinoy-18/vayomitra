
import org.mindrot.jbcrypt.BCrypt;
import java.util.Scanner;

public class CreateAdmin {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Admin email: ");
        String email = sc.nextLine().trim().toLowerCase();

        System.out.print("Choose admin password: ");
        String password = sc.nextLine();

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        System.out.println("EMAIL=" + email);
        System.out.println("HASH=" + hash);
    }
}
