import NextAuth, { DefaultSession } from "next-auth";

declare module "next-auth" {
  interface Session {
    idToken?: string;
    user: {
      id?: string;
    } & DefaultSession["user"];
  }
}
