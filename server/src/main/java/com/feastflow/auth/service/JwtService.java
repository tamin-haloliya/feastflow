package com.feastflow.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    private static final String ISSUER = "feastflow";
    private static final long ACCESS_TOKEN_TTL_SECONDS = 15 * 60;

    public JwtService(@Value("${jwt.private-key:}") String privateKeyPem,
                      @Value("${jwt.public-key:}") String publicKeyPem) {
        if (privateKeyPem != null && !privateKeyPem.isBlank() &&
            publicKeyPem != null && !publicKeyPem.isBlank()) {
            this.privateKey = loadPrivateKey(privateKeyPem);
            this.publicKey = loadPublicKey(publicKeyPem);
        } else {
            KeyPair keyPair = generateKeyPair();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
        }
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    private RSAPrivateKey loadPrivateKey(String pem) {
        try {
            String cleanedPem = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = java.util.Base64.getDecoder().decode(cleanedPem);
            java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(decoded);
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load private key", e);
        }
    }

    private RSAPublicKey loadPublicKey(String pem) {
        try {
            String cleanedPem = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = java.util.Base64.getDecoder().decode(cleanedPem);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(decoded);
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load public key", e);
        }
    }

    public String generateAccessToken(UUID userId, String role) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS);

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("user_id", userId.toString())
                    .claim("role", role)
                    .issuer(ISSUER)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claimsSet
            );

            signedJWT.sign(new RSASSASigner(privateKey));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to generate access token", e);
        }
    }

    public JWTClaimsSet parseAndValidateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            RSASSAVerifier verifier = new RSASSAVerifier(publicKey);

            if (!signedJWT.verify(verifier)) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

            if (claimsSet.getExpirationTime().before(Date.from(Instant.now()))) {
                throw new IllegalArgumentException("Token expired");
            }

            if (!ISSUER.equals(claimsSet.getIssuer())) {
                throw new IllegalArgumentException("Invalid token issuer");
            }

            return claimsSet;
        } catch (JOSEException | java.text.ParseException e) {
            throw new IllegalArgumentException("Invalid token", e);
        }
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public long getAccessTokenTtlSeconds() {
        return ACCESS_TOKEN_TTL_SECONDS;
    }
}