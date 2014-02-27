/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.monacoin.params;

import com.google.monacoin.core.NetworkParameters;
import com.google.monacoin.core.Utils;
import org.spongycastle.util.encoders.Hex;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the testnet, a separate public instance of Bitcoin that has relaxed rules suitable for development
 * and testing of applications and new Bitcoin versions.
 */
public class TestNet3Params extends NetworkParameters {
    public TestNet3Params() {
        super();
        id = ID_TESTNET;
        // Genesis hash is a0d810b45c92ac12e8c5b312a680caafba52216e5d9649b9dd86f7ad6550a43f
        packetMagic = 0xfcc1b7dc; // correspond to pchMessageStart in main.cpp
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        proofOfWorkLimit = Utils.decodeCompactBits(0x1e0fffffL);
        port = 19401;
        addressHeader = 111;
        acceptableAddressCodes = new int[] { 111 };
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(1388479759L);
        genesisBlock.setDifficultyTarget(0x1e0ffff0L);
        genesisBlock.setNonce(600389L);
        spendableCoinbaseDepth = 100;  // equivalent to COINBASE_MATURITY ?
        subsidyDecreaseBlockCount = 1051200;
        String genesisHash = genesisBlock.getHashAsString();
        // a0d810b45c92ac12e8c5b312a680caafba52216e5d9649b9dd86f7ad6550a43f
        checkState(genesisHash.equals("a0d810b45c92ac12e8c5b312a680caafba52216e5d9649b9dd86f7ad6550a43f"));
        alertSigningKey = Hex.decode("04887665070e79d20f722857e58ec8f402733f710135521a0b63441419bf5665ba4623bed13fca0cb2338682ab2a54ad13ce07fbc81c3c2f0912a4eb8521dd3cfb");

        dnsSeeds = new String[] {
                "test-dnsseed.monacoin.org",
        };
    }

    private static TestNet3Params instance;
    public static synchronized TestNet3Params get() {
        if (instance == null) {
            instance = new TestNet3Params();
        }
        return instance;
    }
}
