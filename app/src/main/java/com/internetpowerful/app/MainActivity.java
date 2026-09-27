package com.internetpowerful.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView webView;

    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int CREATE_FILE_REQUEST = 1002;

    private byte[] pendingFileBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        /*
         * Puente entre nuestro HTML y Android.
         */
        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                return openExternalUrl(
                        request.getUrl().toString()
                );
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url) {

                return openExternalUrl(url);
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {

                super.onPageFinished(view, url);

                /*
                 * Conectamos window.print() con Android.
                 */
                view.evaluateJavascript(
                        "window.print=function(){" +
                        "if(window.Android){" +
                        "Android.printPage();" +
                        "}" +
                        "};",
                        null
                );

                /*
                 * El respaldo V45 intenta usar navigator.share().
                 * Lo desactivamos para que utilice el mecanismo
                 * de descarga que vamos a interceptar.
                 */
                view.evaluateJavascript(
                        "(function(){" +
                        "try{" +
                        "Object.defineProperty(" +
                        "navigator,'share'," +
                        "{value:undefined,configurable:true}" +
                        ");" +
                        "}catch(e){}" +
                        "})();",
                        null
                );

                /*
                 * Interceptamos los enlaces creados por el HTML
                 * con download="...".
                 *
                 * Esto permite capturar el Blob del respaldo
                 * y enviarlo al selector de archivos de Android.
                 */
                view.evaluateJavascript(
                        "(function(){" +

                        "if(window.__androidBackupBridge)return;" +
                        "window.__androidBackupBridge=true;" +

                        "document.addEventListener('click'," +
                        "function(e){" +

                        "var a=e.target;" +

                        "while(a && a.tagName!=='A')" +
                        "a=a.parentElement;" +

                        "if(!a)return;" +

                        "if(!a.hasAttribute('download'))return;" +

                        "var href=a.href;" +
                        "var name=a.getAttribute('download')||" +
                        "'Internet-Powerful-Respaldo.json';" +

                        "if(!href || href.indexOf('blob:')!==0)" +
                        "return;" +

                        "e.preventDefault();" +
                        "e.stopPropagation();" +

                        "fetch(href)" +
                        ".then(function(r){return r.blob();})" +
                        ".then(function(blob){" +

                        "var reader=new FileReader();" +

                        "reader.onloadend=function(){" +

                        "var result=reader.result;" +

                        "var comma=result.indexOf(',');" +

                        "var base64=result.substring(comma+1);" +

                        "Android.saveBase64File(" +
                        "name," +
                        "blob.type||'application/json'," +
                        "base64" +
                        ");" +

                        "};" +

                        "reader.readAsDataURL(blob);" +

                        "})" +

                        ".catch(function(){" +

                        "Android.showToast(" +
                        "'No se pudo preparar el respaldo'" +
                        ");" +

                        "});" +

                        "},true);" +

                        "})();",
                        null
                );
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onJsAlert(
                    WebView view,
                    String url,
                    String message,
                    final JsResult result) {

                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(
                                "Aceptar",
                                (dialog, which) ->
                                        result.confirm()
                        )
                        .setOnCancelListener(
                                dialog -> result.cancel()
                        )
                        .show();

                return true;
            }

            @Override
            public boolean onJsConfirm(
                    WebView view,
                    String url,
                    String message,
                    final JsResult result) {

                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setNegativeButton(
                                "Cancelar",
                                (dialog, which) ->
                                        result.cancel()
                        )
                        .setPositiveButton(
                                "Aceptar",
                                (dialog, which) ->
                                        result.confirm()
                        )
                        .setOnCancelListener(
                                dialog -> result.cancel()
                        )
                        .show();

                return true;
            }

            @Override
            public boolean onJsPrompt(
                    WebView view,
                    String url,
                    String message,
                    String defaultValue,
                    final JsPromptResult result) {

                final EditText input =
                        new EditText(MainActivity.this);

                input.setSingleLine(false);
                input.setText(defaultValue);

                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setView(input)
                        .setNegativeButton(
                                "Cancelar",
                                (dialog, which) ->
                                        result.cancel()
                        )
                        .setPositiveButton(
                                "Aceptar",
                                (dialog, which) ->
                                        result.confirm(
                                                input.getText().toString()
                                        )
                        )
                        .setOnCancelListener(
                                dialog -> result.cancel()
                        )
                        .show();

                return true;
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                try {

                    Intent intent =
                            params.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                    return true;

                } catch (Exception e) {

                    filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo abrir el selector de archivos",
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }
            }
        });

        webView.loadUrl(
                "file:///android_asset/index.html"
        );
    }

    /*
     * Abrir enlaces externos.
     */
    private boolean openExternalUrl(String url) {

        if (url == null) {
            return false;
        }

        try {

            if (url.startsWith("whatsapp://")
                    || url.startsWith("https://wa.me/")
                    || url.startsWith("https://api.whatsapp.com/")) {

                Intent intent =
                        new Intent(Intent.ACTION_VIEW);

                intent.setData(Uri.parse(url));

                startActivity(intent);

                return true;
            }

            if (url.startsWith("tel:")) {

                Intent intent =
                        new Intent(Intent.ACTION_DIAL);

                intent.setData(Uri.parse(url));

                startActivity(intent);

                return true;
            }

            if (url.startsWith("mailto:")) {

                Intent intent =
                        new Intent(Intent.ACTION_SENDTO);

                intent.setData(Uri.parse(url));

                startActivity(intent);

                return true;
            }

            if (url.startsWith("http://")
                    || url.startsWith("https://")) {

                Intent intent =
                        new Intent(Intent.ACTION_VIEW);

                intent.setData(Uri.parse(url));

                startActivity(intent);

                return true;
            }

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "No se pudo abrir el enlace",
                    Toast.LENGTH_SHORT
            ).show();

            return true;
        }

        return false;
    }

    /*
     * Puente JavaScript -> Android.
     */
    private class AndroidBridge {

        @JavascriptInterface
        public void showToast(String message) {

            runOnUiThread(() ->
                    Toast.makeText(
                            MainActivity.this,
                            message,
                            Toast.LENGTH_SHORT
                    ).show()
            );
        }

        @JavascriptInterface
        public void printPage() {

            runOnUiThread(() -> {

                try {

                    PrintManager printManager =
                            (PrintManager)
                                    getSystemService(
                                            Context.PRINT_SERVICE
                                    );

                    PrintDocumentAdapter adapter =
                            webView.createPrintDocumentAdapter(
                                    "Internet Powerful"
                            );

                    PrintAttributes attributes =
                            new PrintAttributes.Builder()
                                    .setMediaSize(
                                            PrintAttributes.MediaSize.NA_LETTER
                                    )
                                    .setMinMargins(
                                            PrintAttributes.Margins.NO_MARGINS
                                    )
                                    .build();

                    printManager.print(
                            "Internet Powerful",
                            adapter,
                            attributes
                    );

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo abrir la impresión",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }

        @JavascriptInterface
        public void saveBase64File(
                String fileName,
                String mimeType,
                String base64Data) {

            try {

                pendingFileBytes =
                        Base64.decode(
                                base64Data,
                                Base64.DEFAULT
                        );

                Intent intent =
                        new Intent(
                                Intent.ACTION_CREATE_DOCUMENT
                        );

                intent.addCategory(
                        Intent.CATEGORY_OPENABLE
                );

                intent.setType(
                        mimeType != null
                                ? mimeType
                                : "application/json"
                );

                intent.putExtra(
                        Intent.EXTRA_TITLE,
                        fileName
                );

                startActivityForResult(
                        intent,
                        CREATE_FILE_REQUEST
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "No se pudo preparar el respaldo",
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        /*
         * Restaurar archivo JSON.
         */
        if (requestCode == FILE_CHOOSER_REQUEST) {

            if (filePathCallback == null) {
                return;
            }

            Uri[] results = null;

            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null) {

                results =
                        new Uri[]{
                                data.getData()
                        };
            }

            filePathCallback.onReceiveValue(results);

            filePathCallback = null;
        }

        /*
         * Guardar respaldo JSON.
         */
        if (requestCode == CREATE_FILE_REQUEST) {

            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null
                    && pendingFileBytes != null) {

                Uri uri = data.getData();

                try {

                    OutputStream outputStream =
                            getContentResolver()
                                    .openOutputStream(uri);

                    if (outputStream == null) {
                        throw new IOException(
                                "No se pudo abrir el archivo"
                        );
                    }

                    outputStream.write(
                            pendingFileBytes
                    );

                    outputStream.flush();
                    outputStream.close();

                    Toast.makeText(
                            this,
                            "Respaldo guardado correctamente",
                            Toast.LENGTH_LONG
                    ).show();

                } catch (Exception e) {

                    Toast.makeText(
                            this,
                            "Error al guardar el respaldo",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

            pendingFileBytes = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
