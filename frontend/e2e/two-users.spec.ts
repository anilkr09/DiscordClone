import { expect, Page, test } from "@playwright/test";

const run = Date.now().toString(36).slice(-6);

const register = async (page: Page, username: string) => {
  await page.goto("/register");
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(`${username}@example.com`);
  await page.locator("#password").fill("e2e-test-password");
  await page.getByRole("button", { name: "Register" }).click();
  await expect(page).toHaveURL(/\/channels/);
};

const say = async (page: Page, text: string) => {
  const input = page.getByPlaceholder(/^Message /);
  await input.fill(text);
  await input.press("Enter");
  await expect(page.getByText(text, { exact: true })).toBeVisible();
};

test("two people become friends, message each other and see presence", async ({ browser }) => {
  const aliceName = `Alice${run}`;
  const bobName = `Bob${run}`;
  const aliceContext = await browser.newContext();
  const bobContext = await browser.newContext();
  const alice = await aliceContext.newPage();
  const bob = await bobContext.newPage();
  await register(alice, aliceName);
  await register(bob, bobName);

  await test.step("befriend", async () => {
    await alice.getByRole("tab", { name: "Add Friend" }).click();
    await alice.getByPlaceholder("Enter a username").fill(bobName);
    await alice.getByRole("button", { name: "Send Friend Request" }).click();

    await bob.getByRole("tab", { name: /Pending/ }).click();
    await bob.getByRole("button", { name: "Accept friend request" }).click();

    // Alice's list updates without a reload, and shows Bob online.
    await alice.getByRole("tab", { name: "Online" }).click();
    await expect(alice.getByRole("button", { name: `Message ${bobName}` })).toBeVisible();
  });

  await test.step("direct messages arrive both ways", async () => {
    await alice.getByRole("button", { name: `Message ${bobName}` }).click();
    await bob.getByRole("tab", { name: "All" }).click();
    await bob.getByRole("button", { name: `Message ${aliceName}` }).click();
    await expect(bob.getByPlaceholder(/^Message /)).toBeVisible();

    await say(alice, `hello bob ${run}`);
    await expect(bob.getByText(`hello bob ${run}`, { exact: true })).toBeVisible();
    await say(bob, `hello alice ${run}`);
    await expect(alice.getByText(`hello alice ${run}`, { exact: true })).toBeVisible();
    await expect(alice.getByText("Not delivered")).toHaveCount(0);
  });

  await test.step("a reload keeps the session and the history", async () => {
    await alice.reload();
    await expect(alice.getByText(`hello alice ${run}`, { exact: true })).toBeVisible();
    await expect(alice.getByText(`hello bob ${run}`, { exact: true })).toHaveCount(1);
  });

  await test.step("presence goes offline after the other person leaves", async () => {
    await alice.goto("/channels/@me");
    await alice.getByRole("tab", { name: "All" }).click();
    await expect(alice.getByRole("img", { name: "Online" }).first()).toBeVisible();

    await bobContext.close();

    await expect(alice.getByRole("img", { name: "Offline" }).first()).toBeVisible({ timeout: 20_000 });
    await expect(alice.getByRole("img", { name: "Online" })).toHaveCount(1); // Alice herself
  });

  await test.step("signing out ends the session", async () => {
    await alice.getByRole("button", { name: /log ?out/i }).click();
    await expect(alice).toHaveURL(/\/login/);
    await alice.goto("/channels/@me");
    await expect(alice).toHaveURL(/\/login/);
  });

  await aliceContext.close();
});
