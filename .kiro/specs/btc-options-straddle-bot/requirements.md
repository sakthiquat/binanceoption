# Requirements Document

## Introduction

This feature implements an automated time-based straddle selling system for BTC options on Binance. The system will execute iron butterfly strategies at regular intervals during a configurable trading window, with automated position management including stop-loss and profit-taking mechanisms.

## Requirements

### Requirement 1

**User Story:** As a trader, I want to configure trading session times, so that I can control when the bot operates within my preferred trading hours.

#### Acceptance Criteria

1. WHEN the system starts THEN it SHALL read job start time from configuration file (default 12:25 PM system time)
2. WHEN the system starts THEN it SHALL read job end time from configuration file (default 1:25 PM system time)
3. WHEN current system time is before job start time THEN the system SHALL wait until start time
4. WHEN current system time exceeds job end time THEN the system SHALL stop all operations and exit

### Requirement 2

**User Story:** As a trader, I want the system to fetch real-time BTC options data from Binance, so that I can execute trades based on current market conditions.

#### Acceptance Criteria

1. WHEN the trading session is active THEN the system SHALL fetch BTCUSDT futures price using Binance API
2. WHEN fetching market data THEN the system SHALL load options chain for current date or next available expiry only
3. WHEN selecting ATM strike THEN the system SHALL choose the strike closest to current BTC futures price
4. WHEN API calls are made THEN the system SHALL use authenticated requests with provided API key and secret
5. IF API calls fail THEN the system SHALL retry with exponential backoff up to 3 attempts

### Requirement 3

**User Story:** As a trader, I want to execute iron butterfly strategies automatically, so that I can capture time decay profits from options.

#### Acceptance Criteria

1. WHEN market data is available THEN the system SHALL identify at-the-money (ATM) put and call options
2. WHEN ATM options are identified THEN the system SHALL sell ATM put and call options with configurable quantity (default 0.01)
3. WHEN selling ATM options THEN the system SHALL buy put and call options at configurable strike distance from ATM (default 10 strikes away)
4. WHEN placing orders THEN the system SHALL use bid/ask prices for limit orders to achieve market-like execution
5. IF limit orders are not filled within 1 second THEN the system SHALL modify orders with more aggressive pricing by going deeper into the order book
6. WHEN modifying sell orders THEN the system SHALL use bid prices or lower to ensure fills
7. WHEN modifying buy orders THEN the system SHALL use ask prices or higher to ensure fills
8. WHEN updating unfilled orders THEN the system SHALL continue modifying every second for up to 1 minute
9. IF orders are not filled within 1 minute THEN the system SHALL send Telegram alert and stop placing orders for that cycle

### Requirement 4

**User Story:** As a trader, I want the system to repeat trading cycles at regular intervals, so that I can capture multiple opportunities during the session.

#### Acceptance Criteria

1. WHEN a trading cycle completes THEN the system SHALL wait for configurable cycle interval (default 5 minutes) before starting next cycle
2. WHEN the trading session is active THEN the system SHALL execute configurable number of trading cycles (default 10 cycles)
3. WHEN configured number of cycles are completed OR session end time is reached THEN the system SHALL stop creating new positions
4. WHEN session end time is reached THEN the system SHALL close all open positions immediately

### Requirement 5

**User Story:** As a trader, I want automated position management with stop-loss and profit targets, so that I can limit losses and secure profits without manual intervention.

#### Acceptance Criteria

1. WHEN positions are open THEN the system SHALL check all sell positions every second
2. WHEN a position shows 30% loss THEN the system SHALL close that position immediately
3. WHEN a position shows 50% profit THEN the system SHALL close that position immediately
4. WHEN calculating position P&L THEN the system SHALL use current market bid/ask prices

### Requirement 6

**User Story:** As a trader, I want portfolio-level risk management, so that I can prevent catastrophic losses from adverse market movements.

#### Acceptance Criteria

1. WHEN positions are open THEN the system SHALL calculate maximum theoretical loss for all open iron butterfly positions combined
2. WHEN current mark-to-market (MTM) reaches 10% of maximum theoretical loss THEN the system SHALL close all positions immediately
3. WHEN portfolio stop-loss is triggered THEN the system SHALL exit the job completely
4. WHEN calculating MTM THEN the system SHALL use real-time option prices from Binance API

### Requirement 7

**User Story:** As a trader, I want secure API configuration, so that I can provide my Binance credentials and trading parameters safely.

#### Acceptance Criteria

1. WHEN the system starts THEN it SHALL read API key and secret from a configuration file
2. WHEN the system starts THEN it SHALL read trading session times from configuration file
3. WHEN reading configuration THEN it SHALL read cycle interval (default 5 minutes) from configuration file
4. WHEN reading configuration THEN it SHALL read number of cycles (default 10) from configuration file
5. WHEN reading configuration THEN it SHALL read strike distance for protective options (default 10 strikes) from configuration file
6. WHEN reading configuration THEN it SHALL read position quantity (default 0.01) from configuration file
7. WHEN reading configuration THEN it SHALL read Telegram bot token and chat ID for alerts from configuration file
8. WHEN reading configuration THEN the system SHALL validate that all required parameters are present
9. IF required configuration is missing THEN the system SHALL log an error and exit gracefully
10. WHEN storing credentials THEN the system SHALL ensure configuration file is not committed to version control

### Requirement 8

**User Story:** As a trader, I want Telegram notifications for critical events, so that I can be alerted to issues requiring manual intervention.

#### Acceptance Criteria

1. WHEN orders are not filled within 1 minute THEN the system SHALL send Telegram alert with order details
2. WHEN portfolio stop-loss is triggered THEN the system SHALL send Telegram alert with portfolio status
3. WHEN system encounters critical errors THEN the system SHALL send Telegram alert with error details
4. WHEN trading session starts and ends THEN the system SHALL send Telegram notifications with session summary

### Requirement 9

**User Story:** As a trader, I want comprehensive logging and monitoring, so that I can track system performance and debug issues.

#### Acceptance Criteria

1. WHEN any trading action occurs THEN the system SHALL log the action with timestamp
2. WHEN orders are placed THEN the system SHALL log order details including price and quantity
3. WHEN positions are closed THEN the system SHALL log closing reason (SL/target/portfolio risk)
4. WHEN errors occur THEN the system SHALL log error details with appropriate severity level
5. WHEN the session completes THEN the system SHALL log summary statistics including total P&L