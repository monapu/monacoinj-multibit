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

        0xa8d6c780, // coind.asicpool.info
        0xcd65b5ca, // vippool.net
        0x37f875db, // jp.lapool.me
        0x8d3c2f4e, // static.141.60.47.78.clients.your-server.de
        0x0f76692e, // fr-db4.suprnova.cc
        0x540af8a2, // c999942263-cloudpro-255838346.cloudatcost.com
        0xb14010a0, // tk2-208-13673.vs.sakura.ne.jp
        0x659e0905, // static.101.158.9.5.clients.your-server.de
        0x981f7a99, // sub0000527837.hmk-temp.com
        0x9e113cc7, // nml-cloud-158.cs.sfu.ca
        0x40092934, // ec2-52-41-9-64.us-west-2.compute.amazonaws.com
        0xcabd202d, // 45.32.189.202.vultr.com
        0x891c5c36, // ec2-54-92-28-137.ap-northeast-1.compute.amazonaws.com
        0x44d7ac4f, // ip-4facd744.taurinet.hu
        0x88034c90, // static.136.3.76.144.clients.your-server.de
        0xd45ee968, // is.mnwt.nl
        0xb5a6d431, // www13167uf.sakura.ne.jp
        0xd8fec780, // mona2.chainsight.info
        0x274d88d5, // mail.mai-akcio.eu
        0x216810a0, // tk2-228-23529.vs.sakura.ne.jp
        
    };

    public void shutdown() {
    }
}
