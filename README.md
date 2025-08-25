# BTC Options Straddle Bot

An automated trading bot for executing iron butterfly strategies on BTC options using the Binance Options API.

## Features

- **Automated Iron Butterfly Execution**: Sells ATM call/put options and buys protective OTM options
- **Configurable Trading Sessions**: Set custom start/end times for trading operations
- **Aggressive Order Filling**: Market-like execution with bid/ask price updates
- **Comprehensive Risk Management**: Individual position and portfolio-level stop-loss controls
- **Real-time Monitoring**: 1-second position monitoring with P&L tracking
- **Telegram Notifications**: Alerts for critical events and trading updates
- **Structured Logging**: Detailed logs for trading actions, orders, positions, and risk events

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- Binance account with Options trading enabled
- Binance API key with Options trading permissions

## Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd btc-options-straddle-bot
   ```

2. **Build the application**
   ```bash
   mvn clean package
   ```

3. **Create configuration file**
   ```bash
   cp config-example.yml config.yml
   ```

4. **Configure your settings** (see Configuration section below)

## Configuration

Edit the `config.yml` file with your settings:

```yaml
trading:
  # REQUIRED: Binance API Credentials
  api-key: "your_binance_api_key_here"
  secret-key: "your_binance_secret_key_here"
  
  # Trading Session Times
  session-start-time: "12:25:00"  # 12:25 PM
  session-end-time: "13:25:00"    # 1:25 PM
  
  # Trading Parameters
  cycle-interval-minutes: 5        # Wait between cycles
  number-of-cycles: 10            # Total cycles per session
  position-quantity: 0.01         # Quantity per option leg
  strike-distance: 10             # Strikes away from ATM
  
  # Risk Management
  stop-loss-percentage: 30.0      # Individual position SL
  profit-target-percentage: 50.0  # Individual position target
  portfolio-risk-percentage: 10.0 # Portfolio-level SL
  
  # OPTIONAL: Telegram Notifications
  telegram-bot-token: "your_bot_token"
  telegram-chat-id: "your_chat_id"
```

### Binance API Setup

1. Log into your Binance account
2. Go to API Management
3. Create a new API key
4. Enable "Options Trading" permission
5. Set IP restrictions for security
6. Copy the API key and secret to your config file

### Telegram Setup (Optional)

1. Create a bot via [@BotFather](https://t.me/BotFather) on Telegram
2. Get your bot token
3. Get your chat ID by messaging [@userinfobot](https://t.me/userinfobot)
4. Add both to your config file

## Usage

### Running the Bot

```bash
# Using Maven
mvn spring-boot:run

# Using JAR file
java -jar target/btc-options-straddle-bot-1.0.0.jar

# With external config
java -jar target/btc-options-straddle-bot-1.0.0.jar --spring.config.location=file:./config.yml
```

### Trading Flow

1. **Session Start**: Bot waits until configured start time
2. **Market Data**: Fetches BTC futures price and options chain
3. **Iron Butterfly**: Executes 4-leg strategy (sell ATM, buy OTM)
4. **Position Monitoring**: Real-time P&L tracking with risk management
5. **Cycle Repeat**: Repeats every N minutes for configured cycles
6. **Session End**: Closes all positions at configured end time

### Logs

The bot creates structured logs in the `logs/` directory:

- `btc-options-bot.log` - Main application log
- `trading-actions.log` - Trading activities
- `orders.log` - Order placement and fills
- `positions.log` - Position creation and management
- `risk-management.log` - Risk events and portfolio monitoring
- `sessions.log` - Session and cycle information
- `errors.log` - Error events only

## Risk Management

### Individual Position Risk
- **Stop Loss**: 30% loss (configurable)
- **Profit Target**: 50% profit (configurable)
- **Real-time Monitoring**: 1-second P&L updates

### Portfolio Risk
- **Portfolio Stop Loss**: 10% of maximum theoretical loss (configurable)
- **Emergency Closure**: All positions closed if threshold exceeded
- **Application Shutdown**: Bot stops if portfolio risk triggered

### Order Management
- **Aggressive Filling**: Updates orders every second with market prices
- **Timeout Handling**: 1-minute timeout with Telegram alerts
- **Market-like Execution**: Uses bid/ask prices for quick fills

## Monitoring

### Telegram Notifications
- Session start/end summaries
- Cycle execution updates
- Position creation/closure alerts
- Risk management events
- Order timeout warnings

### Log Monitoring
- Structured logging with MDC context
- Separate log files by category
- Rolling file appenders with retention
- Error-only log for critical issues

## Safety Features

- **Configuration Validation**: Validates all parameters on startup
- **API Rate Limiting**: Respects Binance API limits
- **Retry Logic**: Exponential backoff for API failures
- **Graceful Shutdown**: Proper cleanup on application exit
- **Position Cleanup**: Closes all positions on session end

## Troubleshooting

### Common Issues

1. **API Connection Errors**
   - Verify API key and secret
   - Check IP restrictions on Binance
   - Ensure Options trading is enabled

2. **Order Timeouts**
   - Check market liquidity
   - Verify position quantities
   - Review strike distance settings

3. **Configuration Errors**
   - Validate YAML syntax
   - Check required parameters
   - Review time format (HH:mm:ss)

### Debug Mode

Enable debug logging by adding to config:
```yaml
logging:
  level:
    com.trading.bot: DEBUG
```

## Disclaimer

This software is for educational purposes only. Trading options involves significant risk and may result in loss of capital. Use at your own risk and ensure you understand the risks involved.

## License

[Add your license information here]