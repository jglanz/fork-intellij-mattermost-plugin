# IntelliJ Mattermost Plugin

This project is a Gradle Kotlin DSL build of the Mattermost integration plugin for IntelliJ-based IDEs. It targets IntelliJ Platform build 252 (2024.3) and later.

## Features

- Configure Mattermost personal access tokens directly from the IDE settings.
- Tool window for browsing teams and channels and posting messages.
- Context action to send the current editor selection to the configured channel.

## Building

```bash
gradle build
```

## Running the IDE Sandbox

```bash
gradle runIde
```

## Configuration

1. Create a [personal access token](https://docs.mattermost.com/manage/personal-access-tokens.html) in Mattermost with the
   permissions required to read teams, view channels, and post messages.
2. Open the IDE settings/preferences dialog and locate **Tools ▸ Mattermost**.
3. Enter the Mattermost server URL (including the protocol) and the personal access token, then apply the changes.
4. Optionally set default team and channel IDs to enable the editor action without opening the tool window first.

## Requirements

- Java 21+
- IntelliJ IDEA 2024.3 or compatible IDE.
