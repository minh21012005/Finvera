import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TcbsRenewalApiError } from "./api/tcbs-renewal";
import { TcbsRenewalPage } from "./tcbs-renewal-page";

const { renewTcbsSession } = vi.hoisted(() => ({ renewTcbsSession: vi.fn() }));
vi.mock("./api/tcbs-renewal", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/tcbs-renewal")>()),
  renewTcbsSession,
}));

describe("TcbsRenewalPage", () => {
  beforeEach(() => renewTcbsSession.mockReset());

  it("submits the chosen OTP method and code, then shows success", async () => {
    renewTcbsSession.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<TcbsRenewalPage />);

    await user.type(screen.getByPlaceholderText(/Nhập mã 6 số/i), "123456");
    await user.click(screen.getByRole("button", { name: /Xác thực/i }));

    expect(await screen.findByRole("status")).toHaveTextContent(/thành công/i);
    expect(renewTcbsSession).toHaveBeenCalledWith("totp", "123456");
  });

  it("surfaces a clear message when the provider is not yet in live mode", async () => {
    renewTcbsSession.mockRejectedValueOnce(new TcbsRenewalApiError(502, "PROVIDER_AUTH_REQUIRED"));
    const user = userEvent.setup();
    render(<TcbsRenewalPage />);

    await user.type(screen.getByPlaceholderText(/Nhập mã 6 số/i), "123456");
    await user.click(screen.getByRole("button", { name: /Xác thực/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/chế độ live TCBS/i);
  });

  it("surfaces the backend detail message for an invalid OTP", async () => {
    renewTcbsSession.mockRejectedValueOnce(new TcbsRenewalApiError(400, "INVALID_REQUEST", "otp is required"));
    const user = userEvent.setup();
    render(<TcbsRenewalPage />);

    await user.type(screen.getByPlaceholderText(/Nhập mã 6 số/i), "000000");
    await user.click(screen.getByRole("button", { name: /Xác thực/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/otp is required/i);
  });
});
