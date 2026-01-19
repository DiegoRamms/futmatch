package com.devapplab.service.email

 fun getHtmlTemplate(title: String, message: String, code: String): String {
    val backgroundColor = "#111318" // backgroundDark
    val containerColor = "#1B2023" // surfaceContainerDark
    val textColor = "#E1E2E9" // onBackgroundDark
    val accentColor = "#A7C8FF" // primaryDark
    val secondaryAccent = "#D7BAFB" // tertiaryDark
    val surfaceDim = "#0F1417" // surfaceDimDark

    return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: 'Courier New', Courier, monospace; /* Tech/Code look */
                        background-color: $backgroundColor;
                        color: $textColor;
                        margin: 0;
                        padding: 0;
                    }
                    .container {
                        max-width: 600px;
                        margin: 40px auto;
                        background-color: $containerColor;
                        border: 1px solid #333;
                        /* Sharp corners for Web 3.0 look */
                        border-radius: 0px; 
                        box-shadow: 0 0 30px rgba(0, 0, 0, 0.5);
                        position: relative;
                    }
                    /* Decorative top bar */
                    .container::before {
                        content: '';
                        display: block;
                        height: 4px;
                        background: linear-gradient(90deg, $accentColor, $secondaryAccent);
                        width: 100%;
                    }
                    .header {
                        background-color: $surfaceDim;
                        padding: 40px 20px;
                        text-align: center;
                        border-bottom: 1px solid #333;
                    }
                    .header h1 {
                        margin: 0;
                        font-size: 24px;
                        color: #FFFFFF;
                        text-transform: uppercase;
                        letter-spacing: 4px;
                    }
                    .content {
                        padding: 50px 30px;
                        text-align: center;
                    }
                    .message {
                        font-size: 16px;
                        line-height: 1.6;
                        margin-bottom: 40px;
                        color: $textColor;
                    }
                    .code-box {
                        background: rgba(167, 200, 255, 0.05);
                        border: 1px solid $accentColor;
                        /* Sharp corners */
                        border-radius: 0px; 
                        padding: 20px 40px;
                        display: inline-block;
                        margin: 0 auto;
                        box-shadow: 0 0 15px rgba(167, 200, 255, 0.1);
                        position: relative;
                    }
                    /* Corner accents for code box */
                    .code-box::after {
                        content: '';
                        position: absolute;
                        bottom: -1px;
                        right: -1px;
                        width: 10px;
                        height: 10px;
                        border-bottom: 2px solid $accentColor;
                        border-right: 2px solid $accentColor;
                    }
                    .code-box::before {
                        content: '';
                        position: absolute;
                        top: -1px;
                        left: -1px;
                        width: 10px;
                        height: 10px;
                        border-top: 2px solid $accentColor;
                        border-left: 2px solid $accentColor;
                    }
                    .code {
                        font-size: 36px;
                        font-weight: bold;
                        letter-spacing: 8px;
                        color: $accentColor; 
                        margin: 0;
                        font-family: 'Courier New', Courier, monospace;
                        text-shadow: 0 0 10px rgba(167, 200, 255, 0.5);
                    }
                    .footer {
                        background-color: $surfaceDim;
                        padding: 20px;
                        text-align: center;
                        font-size: 10px;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                        color: #8C9198;
                        border-top: 1px solid #333;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>$title</h1>
                    </div>
                    <div class="content">
                        <p class="message">$message</p>
                        <div class="code-box">
                            <p class="code">$code</p>
                        </div>
                    </div>
                    <div class="footer">
                        <p>&copy; 2026 Futmatch. Decentralizing Football.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
}