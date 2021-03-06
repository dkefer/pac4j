/*
  Copyright 2012 - 2015 pac4j organization

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.pac4j.jwt.credentials.authenticator;

import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.ProfileHelper;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.http.credentials.TokenCredentials;
import org.pac4j.http.credentials.authenticator.TokenAuthenticator;
import org.pac4j.jwt.profile.JwtProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

/**
 * Authenticator for JWT. It creates the user profile and stores it in the credentials
 * for the {@link org.pac4j.http.profile.creator.AuthenticatorProfileCreator}.
 *
 * @author Jerome Leleu
 * @since 1.8.0
 */
public class JwtAuthenticator implements TokenAuthenticator {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private String signingSecret;
    private String encryptionSecret;

    public JwtAuthenticator() {}

    public JwtAuthenticator(final String signingSecret) {
        this(signingSecret, signingSecret);
        warning();
    }

    private void warning() {
        logger.warn("Using the same key for signing and encryption may lead to security vulnerabilities. Consider using different keys");
    }

    /**
     * @since 1.8.2
     */
    public JwtAuthenticator(final String signingSecret, final String encryptionSecret) {
        this.signingSecret = signingSecret;
        this.encryptionSecret = encryptionSecret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validate(final TokenCredentials credentials) {
        CommonHelper.assertNotBlank("signingSecret", signingSecret);

        final String token = credentials.getToken();
        boolean verified = false;
        SignedJWT signedJWT = null;

        try {
            // Parse the token
            final JWT jwt = JWTParser.parse(token);

            if (jwt instanceof SignedJWT) {
                signedJWT = (SignedJWT) jwt;
            } else if (jwt instanceof EncryptedJWT) {
                final JWEObject jweObject = (JWEObject) jwt;
                CommonHelper.assertNotBlank("encryptionSecret", encryptionSecret);
                jweObject.decrypt(new DirectDecrypter(this.encryptionSecret.getBytes("UTF-8")));

                // Extract payload
                signedJWT = jweObject.getPayload().toSignedJWT();
            } else {
                throw new TechnicalException("unsupported unsecured jwt");
            }

            verified = signedJWT.verify(new MACVerifier(this.signingSecret));
        } catch (final Exception e) {
            throw new TechnicalException("Cannot decrypt / verify JWT", e);
        }

        if (!verified) {
            final String message = "JWT verification failed: " + token;
            throw new CredentialsException(message);
        }

        try {
            createJwtProfile(credentials, signedJWT);
        } catch (final Exception e) {
            throw new TechnicalException("Cannot get claimSet", e);
        }
    }

    private static void createJwtProfile(final TokenCredentials credentials, final SignedJWT signedJWT) throws ParseException {
        final JWTClaimsSet claimSet = signedJWT.getJWTClaimsSet();
        String subject = claimSet.getSubject();

        if (!subject.contains(UserProfile.SEPARATOR)) {
            subject = JwtProfile.class.getSimpleName() + UserProfile.SEPARATOR + subject;
        }

        final UserProfile profile = ProfileHelper.buildProfile(subject, claimSet.getClaims());
        credentials.setUserProfile(profile);
    }

    /**
     * @since 1.8.2
     */
    public String getSigningSecret() {
        return signingSecret;
    }

    /**
     * @since 1.8.2
     */
    public void setSigningSecret(final String signingSecret) {
        this.signingSecret = signingSecret;
    }

    /**
     * @since 1.8.2
     */
    public String getEncryptionSecret() {
        return encryptionSecret;
    }

    /**
     * @since 1.8.2
     */
    public void setEncryptionSecret(final String encryptionSecret) {
        this.encryptionSecret = encryptionSecret;
    }

    @Deprecated
    public void setSecret(String secret) {
        this.encryptionSecret = secret;
        this.signingSecret = secret;
        warning();
    }
}
