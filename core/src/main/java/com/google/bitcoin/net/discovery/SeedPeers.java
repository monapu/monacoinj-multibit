/**
 * Copyright 2011 Micheal Swiggs
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
package com.google.bitcoin.net.discovery;

import com.google.bitcoin.core.NetworkParameters;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * SeedPeers stores a pre-determined list of Bitcoin node addresses. These nodes are selected based on being
 * active on the network for a long period of time. The intention is to be a last resort way of finding a connection
 * to the network, in case IRC and DNS fail. The list comes from the Bitcoin C++ source code.
 */
public class SeedPeers implements PeerDiscovery {
    private NetworkParameters params;
    private int pnseedIndex;

    public SeedPeers(NetworkParameters params) {
        this.params = params;
    }

    /**
     * Acts as an iterator, returning the address of each node in the list sequentially.
     * Once all the list has been iterated, null will be returned for each subsequent query.
     *
     * @return InetSocketAddress - The address/port of the next node.
     * @throws PeerDiscoveryException
     */
    @Nullable
    public InetSocketAddress getPeer() throws PeerDiscoveryException {
        try {
            return nextPeer();
        } catch (UnknownHostException e) {
            throw new PeerDiscoveryException(e);
        }
    }

    @Nullable
    private InetSocketAddress nextPeer() throws UnknownHostException {
        if (pnseedIndex >= seedAddrs.length) return null;
        return new InetSocketAddress(convertAddress(seedAddrs[pnseedIndex++]),
                params.getPort());
    }

    /**
     * Returns an array containing all the Bitcoin nodes within the list.
     */
    public InetSocketAddress[] getPeers(long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
        try {
            return allPeers();
        } catch (UnknownHostException e) {
            throw new PeerDiscoveryException(e);
        }
    }

    private InetSocketAddress[] allPeers() throws UnknownHostException {
        InetSocketAddress[] addresses = new InetSocketAddress[seedAddrs.length];
        for (int i = 0; i < seedAddrs.length; ++i) {
            addresses[i] = new InetSocketAddress(convertAddress(seedAddrs[i]), params.getPort());
        }
        return addresses;
    }

    private InetAddress convertAddress(int seed) throws UnknownHostException {
        byte[] v4addr = new byte[4];
        v4addr[0] = (byte) (0xFF & (seed));
        v4addr[1] = (byte) (0xFF & (seed >> 8));
        v4addr[2] = (byte) (0xFF & (seed >> 16));
        v4addr[3] = (byte) (0xFF & (seed >> 24));
        return InetAddress.getByAddress(v4addr);
    }

    public static int[] seedAddrs =
    {
        0x3df85edb, 0x510d213c, 0x65f8013a, 0x6f693db4, 0x972e073d, 0xd0151672, 0x61ff83d3, 0x75cbf285,
        0xcf633624, 0x4a218c1b, 0xcee0c57d, 0x8aebf285, 0x0e33601b, 0x4854d431, 0x62aea9b6, 0x180df577,
        0x53237a99, 0x6f1a0176, 0x8aa43a74, 0x6a35601b, 0x4990d431, 0x668c1e73, 0x75433624, 0x87a4942a,
        0xcd4f297c, 0x652b01b4, 0x870f7edb, 0x869a079d, 0x4fa7d431, 0x7889079d, 0x290af285, 0x5beef1c0,
        0x7efc88d2, 0xc6d213db, 0xddf85edb, 0x99db9f72, 0x366e877a, 0x043ffe74, 0x50e8f285, 0xaead8531,
        0xc8cfa1ca, 0x99d8557c, 0xf3cd623a, 0xdaa55b3a, 0x4bbf397e, 0x4774c836, 0x9eebf285, 0x27f2b3af,
        0xc0f0f376, 0x271baab6, 0x78988d4a, 0x59277899, 0xb905357d, 0xb865ca85, 0xb4bb367d, 0x24073385,
        0x86cb079d, 0xd724ca3d, 0x2568a499, 0xb5f16eda, 0xcd65b5ca, 0x3a396fdb, 0x5b337999, 0x7b0d6c75,
        0xc79bc4b4, 0x32ae067e, 0x1afc9671, 0x6945f9c0, 0x2b367999, 0xb5d1079d, 0x8aef5edb, 0xde257899,
        0x29616f79, 0x83529572, 0x62bca699, 0x3b554470, 0xeee73724, 0xca7268db, 0xe73c7999, 0xc9e415b6,
        0x40bcbe99, 0x52b7bb99, 0x48037899, 0xd8edf285, 0xa42f6c75, 0x403c7999, 0xc1360d85, 0xfe1a1672,
        0x36ea03b4, 0x8410d779, 0x853b2573, 0x73780476, 0x3ad1c09d, 0x50cef176, 0x50ca85d3, 0xe3612fb4,
        0x34b7f031, 0x9b05a099, 0xe7e8016e, 0x25214fd2, 0x5a644f7e, 0xe6b54bdb, 0x8a997adb, 0xabeb5edb,
        0x76c8686f, 0xe9d0a605, 0x374c800e, 0x53cea699, 0x9ef36976, 0x73fe1676, 0x4c68a5cb, 0x27b80d3a,
        0x0275543b, 0xcc20686f, 0x8a0e9199, 0x4eb6f176, 0x04c0c09d, 0x87c4317a, 0xe3d0007d, 0xc631d07c,
        0xda3e30b4, 0x6f159a72, 0x2b3d987a, 0x5555e07b, 0x3ec7d87b, 0x5b2ea972, 0x5dd9db7b, 0x123f2573,
        0xc0bc2d7c, 0xa80dfb94, 0x57a5da85, 0xa0ba0179, 0x9f2d6b79, 0xdbd6bf3a, 0x0544601b, 0x03950585,
        0xa228573d, 0x40b90a7e, 0xa24eac6a, 0xec53737e, 0x59698e65, 0x088ab5b7, 0xf9001877, 0x0c43d57c,
        0x529cb0b7, 0x6a06b173, 0x85264b78, 0xa7d21e7d, 0x3be21676, 0x98b4d2dc, 0x788cb5b7, 0x33940279,
        0x8100fe7b, 0x07253624, 0x4f4fec85, 0x0286367d, 0x03517b3d, 0xab226edb, 0x15376679, 0xe95e3624,
        0x3306511b, 0x530d9d6a, 0xf25ea63b, 0xeb81d431, 0x6f59017d, 0x59eb5edb, 0x85064570, 0xad898a70,
        0x13c8d085, 0x3cc893b4, 0x1bd908de, 0x76833765, 0xa581871b, 0x082b20b4, 0x024485df,
    };

    public void shutdown() {
    }
}
