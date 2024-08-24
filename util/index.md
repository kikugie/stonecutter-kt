---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

titleTemplate: Stonecutter
title: Stonecutter
description: Modern Gradle plugin for multi-version management

hero:
  name: Stonecutter
  tagline: Modern Gradle plugin for multi-version management
  image:
    src: /assets/logo.webp
    alt: Stonecutter

features:
  - title: Migrating to Stonecutter
    icon: 🛫
    details: Do you already have a project or want to start from scratch? Take a look on the detailed setup guide.
    link: /stonecutter/migration
    linkText: Get Started
  - title: Quick start
    icon: ⏳
    details: Check out the Fabric mod template repository to start a new mod with multi-version support.
    link: https://github.com/kikugie/stonecutter-template-fabric
    linkText: Template Repository
  - title: Learn to use Stonecutter
    icon: 🖊
    details: Explore the rich feature set provided by the custom in-comment language used by Stonecutter - Stitcher.
    link: /stonecutter/comments
    linkText: Documentation
  - title: Intellij IDEA plugin
    icon: 🧩
    details: The Intellij plugin is still in early development, but already has a couple useful features.
    link: https://plugins.jetbrains.com/plugin/25044-stonecutter-dev
    linkText: Plugin page
---

<!--suppress ES6UnusedImports, HtmlUnknownAttribute -->
<script setup>
import { VPTeamMembers } from 'vitepress/theme';
import modrinth from '/assets/modrinth.svg?raw';
import curseforge from '/assets/curseforge.svg?raw';

const members = [
%s
];
</script>

## Projects using Stonecutter

*This list is autogenerated. If you find a mistake please report it to the [Issues page](https://github.com/kikugie/stonecutter/issues)*  
*If you want your project to be included or excluded, open a GitHub issue or contact on Discord.*
<VPTeamMembers size="small" :members="members" />