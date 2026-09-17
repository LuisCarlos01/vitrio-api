package dev.vitrio.api.asset;

/**
 * Lançada ao tentar excluir um {@code Asset} já referenciado por {@code Product.imageAssetId} ou
 * {@code Catalog.logoAssetId} (spec 012) — nunca deveria ser possível derrubar a imagem de um
 * produto/loja já existente por engano ao fazer rollback de um upload órfão.
 */
public class AssetInUseException extends RuntimeException {

    public AssetInUseException() {
        super("Asset is in use by a product or catalog and cannot be deleted");
    }
}
