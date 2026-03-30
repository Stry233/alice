#include <gtest/gtest.h>
#include "core/motor/MotorProtocol.h"

using namespace alice;

// ── Command formatting ───────────────────────────────────────────────

TEST(MotorProtocol, FormatCommand) {
    EXPECT_EQ(MotorProtocol::formatCommand("STATUS"), "STATUS\r\n");
    EXPECT_EQ(MotorProtocol::formatCommand("HELP"), "HELP\r\n");
}

TEST(MotorProtocol, FormatPositionCommand) {
    EXPECT_EQ(MotorProtocol::formatPositionCommand(2048), "POS 2048\r\n");
    EXPECT_EQ(MotorProtocol::formatPositionCommand(0), "POS 0\r\n");
    EXPECT_EQ(MotorProtocol::formatPositionCommand(4095), "POS 4095\r\n");
}

TEST(MotorProtocol, FormatPositionClamped) {
    EXPECT_EQ(MotorProtocol::formatPositionCommand(-10), "POS 0\r\n");
    EXPECT_EQ(MotorProtocol::formatPositionCommand(5000), "POS 4095\r\n");
}

TEST(MotorProtocol, FormatDestCommand) {
    // 0xFFFF -> high=255, low=255
    EXPECT_EQ(MotorProtocol::formatDestCommand(0xFFFF), "DEST 255 255\r\n");
    // 0x0100 -> high=1, low=0
    EXPECT_EQ(MotorProtocol::formatDestCommand(0x0100), "DEST 1 0\r\n");
}

TEST(MotorProtocol, FormatScanCommand) {
    EXPECT_EQ(MotorProtocol::formatScanCommand(0x1234), "SCAN 18 52\r\n");
}

// ── Response parsing ─────────────────────────────────────────────────

TEST(MotorProtocol, ParsePositionResponse) {
    auto resp = MotorProtocol::parseResponse("OK:POS=2048");
    EXPECT_TRUE(std::holds_alternative<MotorPositionResponse>(resp));
    EXPECT_EQ(std::get<MotorPositionResponse>(resp).position, 2048);
}

TEST(MotorProtocol, ParseDestResponse) {
    auto resp = MotorProtocol::parseResponse("OK:DEST=FFFF");
    EXPECT_TRUE(std::holds_alternative<MotorDestResponse>(resp));
    EXPECT_EQ(std::get<MotorDestResponse>(resp).address, 0xFFFF);
}

TEST(MotorProtocol, ParseScanResponse) {
    auto resp = MotorProtocol::parseResponse("OK:SCAN=1234");
    EXPECT_TRUE(std::holds_alternative<MotorScanResponse>(resp));
    EXPECT_EQ(std::get<MotorScanResponse>(resp).address, 0x1234);
}

TEST(MotorProtocol, ParseStatusResponse) {
    auto resp = MotorProtocol::parseResponse("Current=1500");
    EXPECT_TRUE(std::holds_alternative<MotorStatusResponse>(resp));
    EXPECT_EQ(std::get<MotorStatusResponse>(resp).position, 1500);
}

TEST(MotorProtocol, ParseCalibrated) {
    auto resp = MotorProtocol::parseResponse("CALIBRATED");
    EXPECT_TRUE(std::holds_alternative<MotorCalibratedResponse>(resp));
}

TEST(MotorProtocol, ParseReady) {
    auto resp = MotorProtocol::parseResponse("Ready");
    EXPECT_TRUE(std::holds_alternative<MotorReadyResponse>(resp));
}

TEST(MotorProtocol, ParseError) {
    auto resp = MotorProtocol::parseResponse("ERROR:timeout");
    EXPECT_TRUE(std::holds_alternative<MotorErrorResponse>(resp));
    EXPECT_EQ(std::get<MotorErrorResponse>(resp).message, "timeout");
}

TEST(MotorProtocol, ParseUnknown) {
    auto resp = MotorProtocol::parseResponse("some random text");
    EXPECT_TRUE(std::holds_alternative<MotorUnknownResponse>(resp));
}

TEST(MotorProtocol, ParseTrimsWhitespace) {
    auto resp = MotorProtocol::parseResponse("  OK:POS=100  \r\n");
    EXPECT_TRUE(std::holds_alternative<MotorPositionResponse>(resp));
    EXPECT_EQ(std::get<MotorPositionResponse>(resp).position, 100);
}

// ── Utilities ────────────────────────────────────────────────────────

TEST(MotorProtocol, IsValidPosition) {
    EXPECT_TRUE(MotorProtocol::isValidPosition(0));
    EXPECT_TRUE(MotorProtocol::isValidPosition(2048));
    EXPECT_TRUE(MotorProtocol::isValidPosition(4095));
    EXPECT_FALSE(MotorProtocol::isValidPosition(-1));
    EXPECT_FALSE(MotorProtocol::isValidPosition(4096));
}

TEST(MotorProtocol, ClampPosition) {
    EXPECT_EQ(MotorProtocol::clampPosition(-100), 0);
    EXPECT_EQ(MotorProtocol::clampPosition(5000), 4095);
    EXPECT_EQ(MotorProtocol::clampPosition(2048), 2048);
}

TEST(MotorProtocol, ParseHexAddress) {
    EXPECT_EQ(MotorProtocol::parseHexAddress("FFFF"), 0xFFFF);
    EXPECT_EQ(MotorProtocol::parseHexAddress("0000"), 0);
    EXPECT_EQ(MotorProtocol::parseHexAddress("1234"), 0x1234);
    EXPECT_EQ(MotorProtocol::parseHexAddress("gggg"), std::nullopt);
}

TEST(MotorProtocol, FormatAddressHex) {
    EXPECT_EQ(MotorProtocol::formatAddressHex(0xFFFF), "FFFF");
    EXPECT_EQ(MotorProtocol::formatAddressHex(0x0001), "0001");
    EXPECT_EQ(MotorProtocol::formatAddressHex(0x1234), "1234");
}
