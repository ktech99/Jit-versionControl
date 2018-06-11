import java.security.MessageDigest;

public class ToHash {
  public static String applyHash(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes("UTF-8"));
      return bytesToHex(hash);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Converting the hash to a String
  // private static String bytesToHex(byte[] hash) {
  //   StringBuffer hexString = new StringBuffer();
  //   for (int i = 0; i < hash.length; i++) {
  //     String hex = Integer.toHexString(0xff & hash[i]);
  //     if (hex.length() == 1) {
  //       hexString.append('0');
  //     }
  //     hexString.append(hex);
  //   }
  //   return hexString.toString();
  // }
}
