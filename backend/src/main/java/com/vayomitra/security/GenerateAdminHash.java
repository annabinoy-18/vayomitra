
package com.vayomitra;

import com.vayomitra.security.Passwords;

public class GenerateAdminHash {
    public static void main(String[] args) {
        System.out.println(
            Passwords.hash("Demo@12345", 10)
        );
    }
}
