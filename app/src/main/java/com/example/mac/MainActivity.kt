package com.example.mac

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.legrange.mikrotik.ApiConnection
import me.legrange.mikrotik.MikrotikApiException
import me.legrange.mikrotik.ResultListener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.InetSocketAddress
import java.io.IOException;
import java.net.Socket;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import android.graphics.Color
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext









class MainActivity : AppCompatActivity() {

    private lateinit var apiConnection: ApiConnection
    private lateinit var progressBar: ProgressBar
    private lateinit var buttonLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        val textViewInternetStatus = findViewById<TextView>(R.id.textViewInternetStatus)
        val editTextIp = findViewById<EditText>(R.id.editTextIp)
        val buttonPing = findViewById<Button>(R.id.buttonPing)
        val textViewResult = findViewById<TextView>(R.id.textViewResult)
        val editTextRouterIp = findViewById<EditText>(R.id.editTextIpAddress)
        val editTextUsername = findViewById<EditText>(R.id.editTextUsername)
        val editTextPassword = findViewById<EditText>(R.id.editTextpassword)
        val editTextPort = findViewById<EditText>(R.id.editTextPort)
        buttonLogin = findViewById(R.id.buttonLogin)
        progressBar = findViewById(R.id.progressBar)
        // API Port Validator views
        val editTextApiIp = findViewById<EditText>(R.id.editTextApiValidationIp)
        val editTextApiPort = findViewById<EditText>(R.id.editTextApiValidationPort)
        val buttonCheckApiPort = findViewById<Button>(R.id.buttonCheckApiPort)
        val textViewApiPortResult = findViewById<TextView>(R.id.textViewApiPortResult)
        // Check internet status
        updateInternetStatus(textViewInternetStatus)

        // Ping functionality
        buttonPing.setOnClickListener {
            val ipAddress = editTextIp.text.toString().trim()
            when {
                ipAddress.isEmpty() -> editTextIp.error = "Please enter an IP address"
                !isValidIpAddress(ipAddress) -> editTextIp.error = "Invalid IP format"
                else -> pingIp(ipAddress, textViewResult)
            }
        }
// API Port Check functionality
        buttonCheckApiPort.setOnClickListener {
            val ip = editTextApiIp.text.toString().trim()
            val port = editTextApiPort.text.toString().trim().toIntOrNull() ?: 8728

            when {
                ip.isEmpty() -> editTextApiIp.error = "Enter IP/Domain"
                else -> checkApiPort(ip, port, textViewApiPortResult)
            }
        }


        buttonPing.setOnClickListener {
            val ipAddress = editTextIp.text.toString().trim()
            when {
                ipAddress.isEmpty() -> editTextIp.error = "Please enter an IP address"
                !isValidIpAddress(ipAddress) -> editTextIp.error = "Invalid IP format"
                else -> pingIp(ipAddress, textViewResult)
            }
        }

        buttonLogin.setOnClickListener {
            val routerIp = editTextRouterIp.text.toString().trim()
            val username = editTextUsername.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            val port = editTextPort.text.toString().trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 8728

            when {
                routerIp.isEmpty() -> editTextRouterIp.error = "Enter Router IP"
                username.isEmpty() -> editTextUsername.error = "Enter Username"
                password.isEmpty() -> editTextPassword.error = "Enter Password"
                else -> connectToRouterOS(routerIp, username, password, port, textViewResult)
            }
        }
    }
    private fun checkApiPort(ip: String, port: Int, textView: TextView) {
        lifecycleScope.launch {
            try {
                textView.text = "Checking port $port..."
                textView.setTextColor(Color.BLACK)
                textView.setBackgroundColor(Color.parseColor("#B2DFDB"))

                val isOpen = withContext(Dispatchers.IO) {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ip, port), 3000)
                            true
                        }
                    } catch (e: IOException) {
                        false
                    }
                }

                if (isOpen) {
                    textView.text = "✅ Port $port is OPEN on $ip"
                    textView.setTextColor(Color.GREEN)
                    textView.setBackgroundColor(Color.parseColor("#E8F5E9"))
                } else {
                    textView.text = "❌ Port $port is CLOSED on $ip"
                    textView.setTextColor(Color.RED)
                    textView.setBackgroundColor(Color.parseColor("#FFEBEE"))
                }
            } catch (e: Exception) {
                textView.text = "⚠️ Error: ${e.message}"
                textView.setTextColor(Color.RED)
                textView.setBackgroundColor(Color.parseColor("#FFF3E0"))
            }
        }
    }

    private fun connectToRouterOS
                (ip: String, username: String, password: String, port: Int, textView: TextView) {
        buttonLogin.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                apiConnection = ApiConnection.connect(SocketFactory.getDefault(), ip, port, 5000)



                apiConnection.login(username, password)

                withContext(Dispatchers.Main) {
                    textView.text = "Connected to RouterOS!\nIP: $ip\nPort: $port"
                    Toast.makeText(this@MainActivity, "Login Successful", Toast.LENGTH_SHORT).show()
                    fetchRouterSystemInfo(apiConnection, textView)
                }
            } catch (e: UnknownHostException) {
                showError(textView, "Invalid Router IP ", e)
            } catch (e: SocketTimeoutException) {
                showError(textView, "Connection Timed Out! Router unreachable.", e)
            } catch (e: MikrotikApiException) {
                showError(textView, "Authentication Failed! Incorrect Username or Password.", e)
            } catch (e: Exception) {
                showError(textView, "Error: ${e.localizedMessage}", e)
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    buttonLogin.isEnabled = true
                }
            }
        }
    }

    private fun fetchRouterSystemInfo(connection: ApiConnection, textView: TextView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = connection.execute("/system/resource/print")
                val systemInfo = StringBuilder()

                for (result in results) {
                    systemInfo.append("Model: ${result["board-name"]}\n")
                    systemInfo.append("Version: ${result["version"]}\n")
                    systemInfo.append("CPU: ${result["cpu"]}\n")
                    systemInfo.append("Memory: ${result["free-memory"]}/${result["total-memory"]}\n")

                }

                withContext(Dispatchers.Main) { textView.text = systemInfo.toString() }
            } catch (e: Exception) {
                showError(textView, "Failed to fetch system info", e)
            }
        }
    }

    private fun isValidIpAddress(ip: String): Boolean {
        val ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".toRegex()
        return ipPattern.matches(ip)
    }

    private fun pingIp(ip: String, textView: TextView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("ping -c 4 $ip")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                reader.forEachLine { output.append(it).append("\n") }

                val exitValue = process.waitFor()
                withContext(Dispatchers.Main) {
                    textView.text = if (exitValue == 0) "Ping successful\n$output" else "Ping failed\n$output"
                }
            } catch (e: Exception) {
                showError(textView, "Ping error", e)
            }
        }
    }

    private fun updateInternetStatus(textView: TextView) {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        textView.text = if (isConnected) "Internet: Connected" else "Internet: Disconnected"
        textView.setTextColor(if (isConnected) getColor(android.R.color.holo_green_dark)
        else getColor(android.R.color.holo_red_dark))
    }

    private fun showError(textView: TextView, message: String, e: Exception) {
        lifecycleScope.launch(Dispatchers.Main) {
            textView.text = message
            Toast.makeText(this@MainActivity, "$message\n${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::apiConnection.isInitialized && apiConnection.isConnected) apiConnection.close()
    }
}
