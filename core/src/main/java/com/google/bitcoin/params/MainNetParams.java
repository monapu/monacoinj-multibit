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

package com.google.bitcoin.params;

import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Utils;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class MainNetParams extends NetworkParameters {
    public MainNetParams() {
        super();
        interval = INTERVAL;
        digishieldInterval = DIGISHIELD_INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        digishieldTargetTimespan = DIGISHIELD_TARGET_TIMESPAN;
        switchKGWBlock = SWITCH_KGW_BLOCK;
        switchDigishieldBlock = SWITCH_DIGISHIELD_BLOCK;
        switchDGWV3Block = SWITCH_DGW_V3_BLOCK;
        switchAlgoLyra2ReV2 = SWITCH_ALGO_LYRA2_RE_V2;
        proofOfWorkLimit = Utils.decodeCompactBits(0x1e0fffffL);
        dumpedPrivateKeyHeader = 178; //This is always addressHeader + 128
        dumpedPrivateKeyHeaderAlt = 176; // monacoin-qt 0.10.x (not modified from litecoin ...)
        addressHeader = 50;
        p2shHeader = 5; // monacoin-qt のp2sh headerはbitcoinから変更されてないように見える
        acceptableAddressCodes = new int[] { addressHeader };
        port = 9401;
        packetMagic = 0xfbc0b6db;
        genesisBlock.setDifficultyTarget(0x1e0ffff0L);
        genesisBlock.setTime(1388479472L);
        genesisBlock.setNonce(1234534L);
        id = ID_MAINNET;
        subsidyDecreaseBlockCount = 1051200;
        spendableCoinbaseDepth = 100;

        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("ff9f1c0116d19de7c9963845e129f9ed1bfc0b376eb54fd7afa42e0d418c8bb6"),
                   genesisBlock);

        // This contains (at a minimum) the blocks which are not BIP30 compliant. BIP30 changed how duplicate
        // transactions are handled. Duplicated transactions could occur in the case where a coinbase had the same
        // extraNonce and the same outputs but appeared at different heights, and greatly complicated re-org handling.
        // Having these here simplifies block connection logic considerably.
        /* checkpoints.put(91722, new Sha256Hash("00000000000271a2dc26e7667f8419f2e15416dc6955e5a6c6cdf3f2574dd08e"));
        checkpoints.put(91812, new Sha256Hash("00000000000af0aed4792b1acee3d966af36cf5def14935db8de83d6f9306f2f"));
        checkpoints.put(91842, new Sha256Hash("00000000000a4d0a398161ffc163c503763b1f4360639393e0e4c8e300e0caec"));
        checkpoints.put(91880, new Sha256Hash("00000000000743f190a18c5577a3c2d2a1f610ae9601ac046a38084ccb7cd721"));
        checkpoints.put(200000, new Sha256Hash("000000000000034a7dedef4a161fa058a2d67a173a90155f3a2fe6fc132e0ebf")); */
        //TODO Get actual Monacoin checkpoints

        dnsSeeds = new String[] {
           "dnsseed.monacoin.org",
           "dnsseed-multimona-test.tk",
           "seed.givememona.tk",
           "api.monaco-ex.org"
                //TODO Add more...
        };
    }

    private static MainNetParams instance;
    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }

    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }
}
