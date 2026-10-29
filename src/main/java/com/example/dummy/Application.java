package com.example.dummy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executors;

public final class Application {

    public static void main(String[] args) throws IOException {
        System.out.println("별 내용은 없고 그냥 출력하는 프로그램");
    }

}
