/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.accessToken.AccessToken.AccessTokenInfo;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import static org.junit.Assert.*;

public class AccessTokenTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    // good case test
    @Test
    public void inputOutputTest() throws InterruptedException, InvalidKeyException,
            NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, TryRefreshTokenException,
            UnsupportedEncodingException, InvalidKeySpecException, SignatureException {
        String[] args = {"../", "DEV"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("key", "value");

        // db key
        TokenInfo newToken = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken");
        AccessTokenInfo info = AccessToken.getInfoFromAccessToken(process.getProcess(), newToken.token, true);
        assertEquals("sessionHandle", info.sessionHandle);
        assertEquals("userId", info.userId);
        assertEquals("refreshTokenHash1", info.refreshTokenHash1);
        assertEquals("parentRefreshTokenHash1", info.parentRefreshTokenHash1);
        assertEquals("value", info.userData.get("key").getAsString());
        assertEquals("antiCsrfToken", info.antiCsrfToken);
        process.kill();
    }


    @Test
    public void signingKeyShortIntervalDoesNotChange() throws InterruptedException,
            StorageQueryException, StorageTransactionLogicException, IOException {
        Utils.setValueInConfig("access_token_signing_key_update_interval", "0.00027"); // 1 second

        String[] args = {"../", "DEV"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        String keyBefore = AccessTokenSigningKey.getInstance(process.getProcess()).getKey().toString();
        Thread.sleep(1500);
        String keyAfter = AccessTokenSigningKey.getInstance(process.getProcess()).getKey().toString();
        assertEquals(keyBefore, keyAfter);
        process.kill();
    }

    @Test
    public void accessTokenShortLifetimeThrowsRefreshTokenError()
            throws IOException, InterruptedException,
            InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException,
            InvalidKeySpecException, SignatureException {
        Utils.setValueInConfig("access_token_validity", "1"); // 1 second

        String[] args = {"../", "DEV"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);

        JsonObject jsonObj = new JsonObject();
        jsonObj.addProperty("key", "value");

        TokenInfo tokenInfo = AccessToken.createNewAccessToken(process.getProcess(), "sessionHandle", "userId",
                "refreshTokenHash1", "parentRefreshTokenHash1", jsonObj, "antiCsrfToken");
        Thread.sleep(1500);

        try {
            AccessToken.getInfoFromAccessToken(process.getProcess(), tokenInfo.token, true);
        } catch (TryRefreshTokenException ex) {
            assertEquals("Access token expired", ex.getMessage());
            process.kill();
            return;
        }
        process.kill();
        fail();
    }

    @Test
    public void verifyRandomAccessTokenFailure() throws InterruptedException,
            StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../", "DEV"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        try {
            AccessToken.getInfoFromAccessToken(process.getProcess(), "token", true);
        } catch (TryRefreshTokenException e) {
            return;
        }
        fail();
    }

}