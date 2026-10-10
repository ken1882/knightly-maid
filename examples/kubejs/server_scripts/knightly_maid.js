// Knightly Maid: extra mounts and carriers from KubeJS (NeoForge 1.21.1, KubeJS 2101).
// Tags are reloaded with /reload.

ServerEvents.tags('entity_type', event => {
  // Mounts she drives herself: any mob that lets a mob rider steer it (horse-like)
  event.add('knightly_maid:maid_mounts', 'minecraft:pig')

  // Carriers she sits on: saddle-click them while carrying her
  event.add('knightly_maid:maid_carriers', 'minecraft:iron_golem')

  // Every player carries her on their back, instead of in their arms (TLM's default)
  event.add('knightly_maid:maid_carriers', 'minecraft:player')

  // Only add modded entities when the mod is there
  if (Platform.isLoaded('iceandfire')) {
    event.add('knightly_maid:maid_carriers', ['iceandfire:fire_dragon', 'iceandfire:ice_dragon', 'iceandfire:lightning_dragon'])
  }

  // Take one out of the defaults
  event.remove('knightly_maid:maid_mounts', 'minecraft:zombie_horse')
})

// Seat offsets and speeds are a data map. The simplest way is the JSON file next to this folder:
//   kubejs/data/knightly_maid/data_maps/entity_type/mount_settings.json
// Or generate it here (check the event name against your KubeJS version):
ServerEvents.generateData('after_mods', event => {
  event.json('knightly_maid:data_maps/entity_type/mount_settings', {
    values: {
      // x left, y up, z forward, from the carrier's feet, turned with its body
      'minecraft:iron_golem': { seat_offset: [0.6, 2.2, 0.0] },
      'minecraft:player': { seat_offset: [0.0, 1.1, -0.4] },
      // Walk target speed while she drives it; without it she matches a player riding it
      'minecraft:pig': { speed_modifier: 1.6 }
    }
  })
})
