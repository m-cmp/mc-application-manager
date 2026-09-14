interface VmClusteringCatalog {
  name?: string
  packageInfo?: { packageName?: string } | null
}

/** UI eligibility only; this does not certify that a deployed cluster is healthy. */
export function isVmClusteringCatalog(catalog?: VmClusteringCatalog | null): boolean {
  const image = String(catalog?.packageInfo?.packageName || '').trim().toLowerCase()
  const imageName = image.split('/').pop()?.split(/[:@]/)[0]
  if (imageName !== 'redis' && imageName !== 'elasticsearch') return false

  // The existing VM backend chooses its port profile from the catalog name,
  // but its runtime configuration from the image name. Both must agree.
  // In particular, Redis exporters/insights are not Redis server clusters.
  const name = String(catalog?.name || '').trim().toLowerCase()
  return new RegExp(`(^|[^a-z0-9])${imageName}([^a-z0-9]|$)`).test(name)
    && !(imageName === 'redis' && name.includes('elasticsearch'))
}
