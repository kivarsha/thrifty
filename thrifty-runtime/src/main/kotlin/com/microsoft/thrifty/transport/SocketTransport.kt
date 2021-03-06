/*
 * Thrifty
 *
 * Copyright (c) Microsoft Corporation
 *
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * THIS CODE IS PROVIDED ON AN  *AS IS* BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING
 * WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE,
 * FITNESS FOR A PARTICULAR PURPOSE, MERCHANTABLITY OR NON-INFRINGEMENT.
 *
 * See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */
package com.microsoft.thrifty.transport

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

class SocketTransport internal constructor(
        builder: Builder
) : Transport {
    private val host = builder.host
    private val port = builder.port
    private val readTimeout = builder.readTimeout
    private val connectTimeout = builder.connectTimeout
    private val socketFactory = builder.socketFactory ?: SocketFactory.getDefault()

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    class Builder(host: String, port: Int) {
        internal val host: String
        internal val port: Int
        internal var readTimeout = 0
        internal var connectTimeout = 0
        internal var socketFactory: SocketFactory? = null

        fun readTimeout(readTimeout: Int): Builder {
            require(readTimeout >= 0) { "readTimeout cannot be negative" }
            this.readTimeout = readTimeout
            return this
        }

        fun connectTimeout(connectTimeout: Int): Builder {
            require(connectTimeout >= 0) { "connectTimeout cannot be negative" }
            this.connectTimeout = connectTimeout
            return this
        }

        fun socketFactory(socketFactory: SocketFactory?): Builder {
            if (socketFactory == null) {
                throw NullPointerException("socketFactory")
            }
            this.socketFactory = socketFactory
            return this
        }

        fun build(): SocketTransport {
            return SocketTransport(this)
        }

        init {
            require(host.isNotBlank()) { "host must not be null or empty" }
            require(port in 0..0xFFFF) { "Invalid port number: $port" }
            this.host = host
            this.port = port
        }
    }

    val isConnected: Boolean
        get() {
            val s = socket
            return s != null && s.isConnected && !s.isClosed
        }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        return inputStream!!.read(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray, offset: Int, count: Int) {
        outputStream!!.write(buffer, offset, count)
    }

    @Throws(IOException::class)
    override fun flush() {
        outputStream!!.flush()
    }

    @Throws(IOException::class)
    fun connect() {
        if (socket == null) {
            socket = socketFactory.createSocket()
        }
        socket!!.tcpNoDelay = true
        socket!!.setSoLinger(false, 0)
        socket!!.keepAlive = true
        socket!!.soTimeout = readTimeout
        socket!!.connect(InetSocketAddress(host, port), connectTimeout)
        inputStream = socket!!.getInputStream()
        outputStream = socket!!.getOutputStream()
    }

    override fun close() {
        val socket = socket
        val `in` = inputStream
        val out = outputStream
        this.socket = null
        if (`in` != null) {
            try {
                `in`.close()
            } catch (ignored: IOException) {
            }
        }
        if (out != null) {
            try {
                out.close()
            } catch (ignored: IOException) {
            }
        }
        if (socket != null) {
            try {
                socket.close()
            } catch (ignored: IOException) {
            }
        }
    }
}
