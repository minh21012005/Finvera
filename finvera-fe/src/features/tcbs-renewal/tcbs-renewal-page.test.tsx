import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TcbsRenewalPage } from "./tcbs-renewal-page";

const api = vi.hoisted(() => ({ getTcbsStatus: vi.fn(), renewTcbsSession: vi.fn() }));
vi.mock("./api/tcbs-renewal", async (original) => ({
  ...(await original<typeof import("./api/tcbs-renewal")>()), ...api,
}));

describe("TcbsRenewalPage", () => {
  beforeEach(() => {
    api.getTcbsStatus.mockResolvedValue({ state: "AUTH_REQUIRED", reasonCode: "PROVIDER_AUTH_REQUIRED" });
    api.renewTcbsSession.mockReset();
  });
  it("submits only the transient OTP", async () => {
    api.renewTcbsSession.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<TcbsRenewalPage />);
    await user.type(screen.getByLabelText(/Mã TOTP/i), "123456");
    await user.click(screen.getByRole("button", { name: /Kết nối live/i }));
    expect(api.renewTcbsSession).toHaveBeenCalledWith("123456");
    expect(await screen.findByRole("status")).toHaveTextContent(/đang kết nối/i);
  });
});
