import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OwnerAccessApiError } from "./api/owner-access";
import { OwnerAccessGate } from "./owner-access-gate";

const { getOwnerSession, loginOwner, logoutOwner } = vi.hoisted(() => ({
  getOwnerSession: vi.fn(), loginOwner: vi.fn(), logoutOwner: vi.fn(),
}));
vi.mock("./api/owner-access", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/owner-access")>()),
  getOwnerSession, loginOwner, logoutOwner,
}));

const session = { subject: "00000000-0000-0000-0000-000000000001", username: "owner", authenticatedAt: "2026-08-17T03:00:00Z", expiresAt: "2026-08-17T11:00:00Z" };

describe("OwnerAccessGate", () => {
  beforeEach(() => { getOwnerSession.mockReset(); loginOwner.mockReset(); logoutOwner.mockReset(); });

  it("obtains an authenticated session before rendering protected content", async () => {
    getOwnerSession.mockResolvedValue(session);
    render(<OwnerAccessGate><p>Protected market</p></OwnerAccessGate>);
    expect(await screen.findByText("Protected market")).toBeVisible();
    expect(screen.getByText(/Phiên riêng tư: owner/)).toBeVisible();
  });

  it("shows login after an anonymous session and submits only through the owner API", async () => {
    getOwnerSession.mockRejectedValueOnce(new OwnerAccessApiError(401));
    loginOwner.mockResolvedValue(session);
    const user = userEvent.setup();
    render(<OwnerAccessGate><p>Protected market</p></OwnerAccessGate>);
    await user.type(await screen.findByLabelText("Username"), "owner");
    await user.type(screen.getByLabelText("Password"), "password-entered-at-runtime");
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));
    expect(await screen.findByText("Protected market")).toBeVisible();
    expect(loginOwner).toHaveBeenCalledWith("owner", "password-entered-at-runtime");
    expect(localStorage).toHaveLength(0);
    expect(sessionStorage).toHaveLength(0);
  });

  it("does not reveal authentication failure details", async () => {
    getOwnerSession.mockRejectedValueOnce(new OwnerAccessApiError(401));
    loginOwner.mockRejectedValueOnce(new OwnerAccessApiError(401));
    const user = userEvent.setup();
    render(<OwnerAccessGate><p>Protected market</p></OwnerAccessGate>);
    await user.type(await screen.findByLabelText("Username"), "owner");
    await user.type(screen.getByLabelText("Password"), "runtime-only-password");
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Đăng nhập không thành công.");
  });
});
