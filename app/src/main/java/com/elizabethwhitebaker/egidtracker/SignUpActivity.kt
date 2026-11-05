package com.elizabethwhitebaker.egidtracker

import android.content.Intent
import android.os.Bundle
import android.text.Annotation
import android.text.SpannableString
import android.text.SpannedString
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SignUpActivity : AppCompatActivity() {

    private lateinit var firstNameEditText: EditText
    private lateinit var lastNameEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var signUpButton: Button
    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var firstNameLayout: TextInputLayout
    private lateinit var lastNameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var phoneLayout: TextInputLayout
    private lateinit var usernameLayout: TextInputLayout

    /** In screen order, so the first field with an error can be focused. */
    private val fieldLayouts
        get() = listOf(firstNameLayout, lastNameLayout, emailLayout, passwordLayout,
            confirmLayout, phoneLayout, usernameLayout)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        firebaseAuth = FirebaseAuth.getInstance()

        firstNameEditText = findViewById(R.id.signUpFirstName)
        lastNameEditText = findViewById(R.id.signUpLastName)
        usernameEditText = findViewById(R.id.signUpUsername)
        phoneEditText = findViewById(R.id.signUpPhone)
        emailEditText = findViewById(R.id.signUpEmail)
        passwordEditText = findViewById(R.id.signUpPassword)
        confirmPasswordEditText = findViewById(R.id.signUpConfirm)
        signUpButton = findViewById(R.id.signUpButton)

        firstNameLayout = findViewById(R.id.firstNameLayout)
        lastNameLayout = findViewById(R.id.lastNameLayout)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmLayout = findViewById(R.id.confirmLayout)
        phoneLayout = findViewById(R.id.phoneLayout)
        usernameLayout = findViewById(R.id.usernameLayout)

        // An error disappears as soon as the user edits that field
        fieldLayouts.forEach { layout -> layout.editText?.doAfterTextChanged { layout.error = null } }

        signUpButton.setOnClickListener { signUpUser() }

        styleTermsLinks(findViewById(R.id.termsText))
    }

    /** Colors and underlines the <annotation type="link"> parts of the terms text. */
    private fun styleTermsLinks(textView: TextView) {
        val source = getText(R.string.terms_agreement) as SpannedString
        val styled = SpannableString(source)
        val linkColor = ContextCompat.getColor(this, R.color.lavender)
        source.getSpans(0, source.length, Annotation::class.java)
            .filter { it.key == "type" && it.value == "link" }
            .forEach { link ->
                val start = source.getSpanStart(link)
                val end = source.getSpanEnd(link)
                styled.setSpan(ForegroundColorSpan(linkColor), start, end, 0)
                styled.setSpan(UnderlineSpan(), start, end, 0)
            }
        textView.text = styled
    }

    private fun signUpUser() {
        val firstName = firstNameEditText.text.toString().trim()
        val lastName = lastNameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        // Not trimmed: spaces are part of the password
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val phoneInput = phoneEditText.text.toString().trim()
        val phone = normalizePhone(phoneInput)
        val usernameInput = usernameEditText.text.toString().trim()
        val username = Usernames.normalize(usernameInput)

        // Check every field at once, so all problems show together under their fields
        fieldLayouts.forEach { it.error = null }
        fun check(layout: TextInputLayout, ok: Boolean, @StringRes error: Int) {
            if (!ok && layout.error == null) layout.error = getString(error)
        }
        check(firstNameLayout, firstName.isNotEmpty(), R.string.error_required)
        check(lastNameLayout, lastName.isNotEmpty(), R.string.error_required)
        check(emailLayout, email.isNotEmpty(), R.string.error_required)
        check(emailLayout, Patterns.EMAIL_ADDRESS.matcher(email).matches(), R.string.error_email_invalid)
        check(passwordLayout, password.isNotEmpty(), R.string.error_required)
        check(passwordLayout, isPasswordValid(password), R.string.password_requirements)
        check(confirmLayout, confirmPassword.isNotEmpty(), R.string.error_required)
        check(confirmLayout, confirmPassword == password, R.string.error_passwords_dont_match)
        check(phoneLayout, phoneInput.isNotEmpty(), R.string.error_required)
        check(phoneLayout, phone != null, R.string.error_phone_invalid)
        check(usernameLayout, usernameInput.isNotEmpty(), R.string.error_required)
        check(usernameLayout, Usernames.isValid(username), R.string.username_requirements_error)

        fieldLayouts.firstOrNull { it.error != null }?.let { invalid ->
            invalid.editText?.requestFocus()
            return
        }

        setSubmitting(true)
        Usernames.ref(username).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    showFieldError(usernameLayout, R.string.error_username_taken)
                } else {
                    createAccount(firstName, lastName, email, password, phone!!, username)
                }
            }
            .addOnFailureListener { e -> showSubmitError(e) }
    }

    private fun createAccount(
        firstName: String, lastName: String, email: String, password: String,
        phone: String, username: String
    ) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener showSubmitError(null)
                // Auth's normalized copy (lowercased); the rules compare against it
                val authEmail = user.email ?: email
                val userMap = hashMapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "email" to authEmail,
                    "username" to username,
                    "phone" to phone
                )

                // Profile and username claim together: if the username was taken in the
                // meantime, the rules reject the claim and neither is written
                val db = Firebase.firestore
                db.batch()
                    .set(db.collection("Users").document(user.uid), userMap)
                    .set(Usernames.ref(username), Usernames.entry(user.uid, authEmail))
                    .commit()
                    .addOnSuccessListener {
                        val intent = Intent(this, AddCaregiverActivity::class.java).apply {
                            putExtra("username", username)
                            putExtra("isFirstTimeUser", true)
                        }
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        // Don't leave a login behind with no profile or username
                        user.delete()
                        val usernameTaken = e is FirebaseFirestoreException &&
                            e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                        if (usernameTaken) showFieldError(usernameLayout, R.string.error_username_taken)
                        else showSubmitError(e)
                    }
            }
            .addOnFailureListener { e ->
                when (e) {
                    is FirebaseAuthUserCollisionException ->
                        showFieldError(emailLayout, R.string.error_email_in_use)
                    is FirebaseAuthWeakPasswordException ->
                        showFieldError(passwordLayout, R.string.password_requirements)
                    is FirebaseAuthInvalidCredentialsException ->
                        showFieldError(emailLayout, R.string.error_email_invalid)
                    else -> showSubmitError(e)
                }
            }
    }

    /** Disables Create Account while a sign-up is in flight, so a double tap can't send two. */
    private fun setSubmitting(submitting: Boolean) {
        signUpButton.isEnabled = !submitting
        signUpButton.alpha = if (submitting) 0.6f else 1f
    }

    private fun showFieldError(layout: TextInputLayout, @StringRes error: Int) {
        setSubmitting(false)
        layout.error = getString(error)
        layout.editText?.requestFocus()
    }

    private fun showSubmitError(e: Exception?) {
        setSubmitting(false)
        val message = if (e is FirebaseNetworkException ||
            (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE)
        ) R.string.error_network else R.string.error_sign_up_failed
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun isPasswordValid(password: String): Boolean {
        val passwordPattern = "^(?=.*[A-Z])(?=.*[!@#\\$])[A-Za-z\\d!@#\\$]{8,}$"
        return Regex(passwordPattern).matches(password)
    }

    /**
     * US phone numbers: any formatting is accepted ("(555) 555-0100", "+1 555 555 0100"), and
     * the number is stored as its 10 digits. Returns null if it isn't a 10-digit number.
     */
    private fun normalizePhone(input: String): String? {
        val digits = input.filter { it.isDigit() }
        val national = if (digits.length == 11 && digits.startsWith("1")) digits.drop(1) else digits
        return national.takeIf { it.length == 10 }
    }
}
