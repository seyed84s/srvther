package com.v2ray.ang.srvx

import java.math.BigInteger
import java.security.SecureRandom

/**
 * Lightweight Curve25519 implementation for WireGuard keypair generation.
 */
object Curve25519 {
    private val P = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
    private val A24 = BigInteger.valueOf(121665)

    fun generatePrivateKey(): ByteArray {
        val random = SecureRandom()
        val key = ByteArray(32)
        random.nextBytes(key)
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = (key[31].toInt() and 127).toByte()
        key[31] = (key[31].toInt() or 64).toByte()
        return key
    }

    fun eval(scalar: ByteArray, point: ByteArray = byteArrayOf(9) + ByteArray(31)): ByteArray {
        val clamped = scalar.clone()
        clamped[0] = (clamped[0].toInt() and 248).toByte()
        clamped[31] = (clamped[31].toInt() and 127).toByte()
        clamped[31] = (clamped[31].toInt() or 64).toByte()

        val k = BigInteger(1, clamped.reversedArray())
        val u = BigInteger(1, point.reversedArray())

        var x1 = u
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = u
        var z3 = BigInteger.ONE

        var swap = 0

        for (t in 254 downTo 0) {
            val kt = k.testBit(t)
            val ktInt = if (kt) 1 else 0
            swap = swap xor ktInt
            if (swap != 0) {
                var tmp = x2; x2 = x3; x3 = tmp
                tmp = z2; z2 = z3; z3 = tmp
            }
            swap = ktInt

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            x3 = da.add(cb).mod(P).pow(2).mod(P)
            z3 = x1.multiply(da.subtract(cb).mod(P).pow(2).mod(P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e).mod(P))).mod(P)
        }

        if (swap != 0) {
            var tmp = x2; x2 = x3; x3 = tmp
            tmp = z2; z2 = z3; z3 = tmp
        }

        val result = x2.multiply(z2.modInverse(P)).mod(P)
        val resBytes = result.toByteArray()
        val out = ByteArray(32)
        val copyLen = minOf(resBytes.size, 32)
        for (i in 0 until copyLen) {
            out[i] = resBytes[resBytes.size - 1 - i]
        }
        return out
    }
}
