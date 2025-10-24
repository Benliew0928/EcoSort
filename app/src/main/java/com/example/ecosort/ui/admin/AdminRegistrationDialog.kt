package com.example.ecosort.ui.admin

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.ecosort.R
import com.example.ecosort.utils.SecurityManager

class AdminRegistrationDialog : DialogFragment() {

    interface AdminRegistrationListener {
        fun onAdminPasskeyVerified(passkey: String)
        fun onAdminRegistrationCancelled()
    }

    private var listener: AdminRegistrationListener? = null

    companion object {
        fun newInstance(listener: AdminRegistrationListener): AdminRegistrationDialog {
            val dialog = AdminRegistrationDialog()
            dialog.listener = listener
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireActivity())
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_admin_registration, null)

        val etPasskey = view.findViewById<EditText>(R.id.etAdminRegistrationPasskey)
        etPasskey.inputType = InputType.TYPE_CLASS_NUMBER
        etPasskey.transformationMethod = PasswordTransformationMethod() // Hide input

        builder.setView(view)
            .setTitle("Admin Passkey Required for Registration")
            .setMessage("Please enter the admin passkey to register as an administrator.")
            .setPositiveButton("Verify", null) // Set to null to prevent auto-dismiss
            .setNegativeButton("Cancel") { dialog, _ ->
                listener?.onAdminRegistrationCancelled()
                dialog.dismiss()
            }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val passkey = etPasskey.text.toString()
                if (SecurityManager.verifyAdminPasskey(passkey)) {
                    listener?.onAdminPasskeyVerified(passkey)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Incorrect Admin Passkey", Toast.LENGTH_SHORT).show()
                    etPasskey.error = "Incorrect Passkey"
                }
            }
        }
        return dialog
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        listener?.onAdminRegistrationCancelled()
    }
}
