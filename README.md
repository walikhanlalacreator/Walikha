# MindView Crypto Scalper AI

A Vite + React + TypeScript single-page app added inside the Walikha repository at /mindview.

Features implemented in MVP:
- Responsive React frontend with Tailwind CSS
- Live 24h ticker fetching from Binance for a default watchlist
- Coin analysis page fetching 15m candles and computing EMA, RSI, MACD, ATR, VWAP
- Risk calculator basic UI
- Local unit tests for EMA and RSI using Vitest
- GitHub Actions workflow (node) to run tests and build

Limitations / Remaining work:
- WebSocket real-time streaming not yet implemented (REST polling currently)
- Full trading signal engine and backtesting modules are scaffolds and need further work
- More comprehensive unit tests (MACD, ATR, VWAP, backtesting) to add

How to run (in /mindview):

1. cd mindview
2. npm install
3. npm run dev

Tests:

npm run test

Build:

npm run build

Market data limits:
Uses Binance public REST endpoints without API key. Rate limits may apply. The app handles basic errors but be cautious.

Financial disclaimer: This tool is for educational analysis only. It does NOT provide investment advice.
