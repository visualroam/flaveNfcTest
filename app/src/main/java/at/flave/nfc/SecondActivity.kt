package at.flave.nfc

import android.app.Activity
import android.os.Bundle

class SecondActivity : FlaveNFCActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)
    }

}