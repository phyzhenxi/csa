package csa.client;

import csa.gateway.LssGateway;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

/** Boot LssGateway on 127.0.0.1:1234 for integration smoke. */
public final class GatewayMain {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 1234;
        try (ServerSocket ss = new ServerSocket()) {
            ss.bind(new InetSocketAddress("127.0.0.1", port));
            System.out.println("LssGateway listening on 127.0.0.1:" + port);
            new LssGateway().serve(ss);
        }
    }
}