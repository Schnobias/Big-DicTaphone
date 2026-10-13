package com.bigdictaphone.app.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import android.util.Log

class SmtpEmailService {
    
    private val TAG = "SmtpEmailService"

    suspend fun sendEmail(
        host: String,
        port: Int,
        user: String,
        pass: String,
        to: String,
        subject: String,
        body: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val props = Properties()
            props["mail.smtp.auth"] = "true"
            require(port in 1..65535) { "Invalid SMTP port." }
            props["mail.smtp.ssl.enable"] = (port == 465).toString()
            props["mail.smtp.starttls.enable"] = (port != 465).toString()
            props["mail.smtp.starttls.required"] = (port != 465).toString()
            props["mail.smtp.ssl.checkserveridentity"] = "true"
            props["mail.smtp.connectiontimeout"] = "15000"
            props["mail.smtp.timeout"] = "30000"
            props["mail.smtp.writetimeout"] = "30000"
            props["mail.smtp.host"] = host
            props["mail.smtp.port"] = port.toString()

            val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(user, pass)
                }
            })

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(user))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            message.subject = subject
            message.setText(body, "utf-8") // Assuming plain text for now, could become html with setContent(body, "text/html")

            Transport.send(message)
            Log.i(TAG, "Email sent successfully")
            Result.success(Unit)
        } catch (e: MessagingException) {
            Log.e(TAG, "Failed to send email", e)
            Result.failure(e)
        } catch (e: Exception) {
             Log.e(TAG, "Unexpected error sending email", e)
             Result.failure(e)
        }
    }
}
