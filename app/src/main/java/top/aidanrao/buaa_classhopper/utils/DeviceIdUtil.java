package top.aidanrao.buaa_classhopper.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.UUID;

import javax.crypto.Cipher;

public class DeviceIdUtil {
    private static final String KEY_ALIAS = "BUAA_ClassHopper_AppIdKey";
    private static final String PREF_NAME = "device_info";
    private static final String ENCRYPTED_ID_KEY = "encrypted_uuid";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";

    public synchronized static String getPersistentUUID(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String encryptedId = prefs.getString(ENCRYPTED_ID_KEY, null);

            if (encryptedId == null) {
                String deviceId = getDeviceId(context);
                String newId = deviceId != null ? deviceId : UUID.randomUUID().toString();
                String encrypted = encryptData(newId);
                prefs.edit().putString(ENCRYPTED_ID_KEY, encrypted).apply();
                return newId;
            } else {
                return decryptData(encryptedId);
            }
        } catch (Exception e) {
            Log.e("DeviceId", "Error when get uuid " + e.getMessage());
            String deviceId = getDeviceId(context);
            if (!TextUtils.isEmpty(deviceId)) {
                return deviceId;
            }
            return UUID.randomUUID().toString();
        }
    }

    private static String getDeviceId(Context context) {
        @SuppressLint("HardwareIds") String androidId = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
        if (TextUtils.isEmpty(androidId)) {
            return null;
        }
        if ("9774d56d682e549c".equals(androidId)) {
            return null;
        }
        return androidId;
    }

    // 生成密钥并加密
    private static String encryptData(String data) throws Exception {
        initKeyStore();
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
        
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.getCertificate().getPublicKey());
        byte[] bytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    // 解密数据
    private static String decryptData(String encryptedData) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_ALIAS, null);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());
        byte[] bytes = Base64.decode(encryptedData, Base64.DEFAULT);
        return new String(cipher.doFinal(bytes), StandardCharsets.UTF_8);
    }

    // 初始化 KeyStore 密钥
    private static void initKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER);
            
            kpg.initialize(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .build());
            kpg.generateKeyPair();
        }
    }
}
