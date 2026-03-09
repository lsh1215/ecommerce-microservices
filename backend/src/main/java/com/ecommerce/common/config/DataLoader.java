package com.ecommerce.common.config;

import com.ecommerce.customer.domain.model.Customer;
import com.ecommerce.customer.domain.model.CustomerAddress;
import com.ecommerce.customer.domain.repository.CustomerAddressRepository;
import com.ecommerce.customer.domain.repository.CustomerRepository;
import com.ecommerce.drop.domain.model.DropEvent;
import com.ecommerce.drop.domain.model.DropProduct;
import com.ecommerce.drop.domain.repository.DropEventRepository;
import com.ecommerce.drop.domain.repository.DropProductRepository;
import com.ecommerce.infrastructure.domain.model.ExchangeRate;
import com.ecommerce.infrastructure.domain.repository.ExchangeRateRepository;
import com.ecommerce.inventory.domain.model.Inventory;
import com.ecommerce.inventory.domain.repository.InventoryRepository;
import com.ecommerce.product.domain.model.Brand;
import com.ecommerce.product.domain.model.Product;
import com.ecommerce.product.domain.model.ProductImage;
import com.ecommerce.product.domain.model.ProductTranslation;
import com.ecommerce.product.domain.model.ProductVariant;
import com.ecommerce.product.domain.repository.BrandRepository;
import com.ecommerce.product.domain.repository.ProductImageRepository;
import com.ecommerce.product.domain.repository.ProductRepository;
import com.ecommerce.product.domain.repository.ProductTranslationRepository;
import com.ecommerce.product.domain.repository.ProductVariantRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryRepository inventoryRepository;
    private final DropEventRepository dropEventRepository;
    private final DropProductRepository dropProductRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final EntityManager entityManager;

    private static final BigDecimal USD_TO_KRW = new BigDecimal("1350.00");
    private static final BigDecimal USD_TO_JPY = new BigDecimal("150.00");
    private static final BigDecimal KRW_TO_JPY = new BigDecimal("0.1111");
    private static final BigDecimal JPY_TO_KRW = new BigDecimal("9.00");

    private static final String[] CATEGORIES = {
            "DENIM", "SHIRT", "JACKET", "PANTS", "ACCESSORY", "OUTERWEAR", "FOOTWEAR", "KNIT"
    };

    private static final String[] ERAS = {
            "1940s_MILITARY", "1950s_WORKWEAR", "1960s_AMERICANA", "1970s_VINTAGE"
    };

    private static final String[] APPAREL_SIZES = {"XS", "S", "M", "L", "XL", "XXL"};
    private static final String[] DENIM_SIZES = {"28", "29", "30", "31", "32", "33", "34", "36", "38"};
    private static final String[] FOOTWEAR_SIZES = {"7", "7.5", "8", "8.5", "9", "9.5", "10", "10.5", "11", "12"};

    private int skuCounter = 1;

    @Override
    @Transactional
    public void run(String... args) {
        if (brandRepository.count() > 0) {
            log.info("Seed data already exists, skipping DataLoader");
            return;
        }

        log.info("Starting seed data generation...");
        long start = System.currentTimeMillis();

        seedExchangeRates();
        seedCustomers();
        List<Brand> brands = seedBrands();
        List<ProductVariant> allVariants = seedProducts(brands);
        seedDropEvents(allVariants);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Seed data generation completed in {} ms", elapsed);
    }

    private void seedExchangeRates() {
        LocalDate today = LocalDate.now();
        List<ExchangeRate> rates = List.of(
                ExchangeRate.create("USD", "KRW", USD_TO_KRW, today),
                ExchangeRate.create("KRW", "USD", BigDecimal.ONE.divide(USD_TO_KRW, 8, RoundingMode.HALF_UP), today),
                ExchangeRate.create("USD", "JPY", USD_TO_JPY, today),
                ExchangeRate.create("JPY", "USD", BigDecimal.ONE.divide(USD_TO_JPY, 8, RoundingMode.HALF_UP), today),
                ExchangeRate.create("KRW", "JPY", KRW_TO_JPY, today),
                ExchangeRate.create("JPY", "KRW", JPY_TO_KRW, today)
        );
        exchangeRateRepository.saveAll(rates);
        log.info("Seeded {} exchange rates", rates.size());
    }

    private void seedCustomers() {
        String hashedPassword = BCrypt.hashpw("password123", BCrypt.gensalt());

        Customer c1 = Customer.create("john.doe@example.com", hashedPassword, "John Doe");
        Customer c2 = Customer.create("tanaka.yuki@example.com", hashedPassword, "Tanaka Yuki");
        Customer c3 = Customer.create("kim.minjun@example.com", hashedPassword, "Kim Minjun");
        Customer c4 = Customer.create("sarah.connor@example.com", hashedPassword, "Sarah Connor");
        Customer c5 = Customer.create("marco.rossi@example.com", hashedPassword, "Marco Rossi");
        List<Customer> customers = customerRepository.saveAll(List.of(c1, c2, c3, c4, c5));
        entityManager.flush();

        List<CustomerAddress> addresses = List.of(
                CustomerAddress.create(customers.get(0), "Home", "John Doe",
                        "+1-503-555-0142", "742 Evergreen Terrace", "Apt 3B",
                        "Portland", "OR", "97201", "US", true),
                CustomerAddress.create(customers.get(0), "Office", "John Doe",
                        "+1-503-555-0199", "1200 NW Marshall St", "Suite 400",
                        "Portland", "OR", "97209", "US", false),
                CustomerAddress.create(customers.get(1), "Home", "Tanaka Yuki",
                        "+81-3-5555-0123", "1-2-3 Jingumae", "Harajuku Heights 501",
                        "Shibuya-ku, Tokyo", "Tokyo", "150-0001", "JP", true),
                CustomerAddress.create(customers.get(2), "Home", "Kim Minjun",
                        "+82-2-555-0187", "12 Apgujeong-ro 50-gil", null,
                        "Gangnam-gu, Seoul", "Seoul", "06011", "KR", true),
                CustomerAddress.create(customers.get(3), "Home", "Sarah Connor",
                        "+1-213-555-0176", "2029 Century Park East", null,
                        "Los Angeles", "CA", "90067", "US", true),
                CustomerAddress.create(customers.get(4), "Home", "Marco Rossi",
                        "+39-02-555-0134", "Via Monte Napoleone 8", "Int. 2",
                        "Milano", "Lombardia", "20121", "IT", true)
        );
        customerAddressRepository.saveAll(addresses);
        log.info("Seeded {} customers with {} addresses", customers.size(), addresses.size());
    }

    private List<Brand> seedBrands() {
        List<Brand> brands = List.of(
                Brand.create("Iron Heart", "iron-heart", "JP", "HEAVYWEIGHT_DENIM",
                        2003, "Japanese heavyweight denim specialists known for 21oz+ selvedge fabrics",
                        "https://cdn.foundry.com/brands/iron-heart-logo.png"),
                Brand.create("The Real McCoy's", "the-real-mccoys", "JP", "MILITARY_REPRO",
                        1988, "Meticulous reproductions of American military and workwear garments from the 1930s-1960s",
                        "https://cdn.foundry.com/brands/real-mccoys-logo.png"),
                Brand.create("Buzz Rickson's", "buzz-ricksons", "JP", "MILITARY_REPRO",
                        1993, "Precision reproductions of WWII-era flight jackets and military apparel",
                        "https://cdn.foundry.com/brands/buzz-ricksons-logo.png"),
                Brand.create("Warehouse & Co.", "warehouse-co", "JP", "VINTAGE_REPRO",
                        1995, "Vintage American workwear reproductions with period-correct construction",
                        "https://cdn.foundry.com/brands/warehouse-logo.png"),
                Brand.create("The Flat Head", "the-flat-head", "JP", "HEAVYWEIGHT_DENIM",
                        1996, "Handcrafted denim and leather goods with proprietary fabrics",
                        "https://cdn.foundry.com/brands/flat-head-logo.png"),
                Brand.create("Studio D'Artisan", "studio-dartisan", "JP", "HERITAGE_DENIM",
                        1979, "One of Osaka's original five denim pioneers with hand-woven selvedge",
                        "https://cdn.foundry.com/brands/studio-dartisan-logo.png"),
                Brand.create("Sugar Cane", "sugar-cane", "JP", "VINTAGE_REPRO",
                        1975, "Heritage denim using natural sugar cane fibers blended with cotton",
                        "https://cdn.foundry.com/brands/sugar-cane-logo.png"),
                Brand.create("Mister Freedom", "mister-freedom", "US", "AMERICANA",
                        2003, "Los Angeles-based brand fusing vintage Americana with global workwear traditions",
                        "https://cdn.foundry.com/brands/mister-freedom-logo.png"),
                Brand.create("Filson", "filson", "US", "OUTDOOR_HERITAGE",
                        1897, "Seattle outfitter producing rugged outdoor clothing and bags since the Klondike Gold Rush",
                        "https://cdn.foundry.com/brands/filson-logo.png"),
                Brand.create("Red Wing", "red-wing", "US", "HERITAGE_FOOTWEAR",
                        1905, "Heritage work boots handcrafted in Red Wing, Minnesota",
                        "https://cdn.foundry.com/brands/red-wing-logo.png"),
                Brand.create("Visvim", "visvim", "JP", "ARTISAN_HERITAGE",
                        2000, "Avant-garde Japanese label blending traditional craftsmanship with modern design",
                        "https://cdn.foundry.com/brands/visvim-logo.png"),
                Brand.create("Kapital", "kapital", "JP", "ARTISAN_HERITAGE",
                        1984, "Okayama-based label known for indigo dyeing techniques and deconstructed silhouettes",
                        "https://cdn.foundry.com/brands/kapital-logo.png")
        );
        List<Brand> saved = brandRepository.saveAll(brands);
        entityManager.flush();
        log.info("Seeded {} brands", saved.size());
        return saved;
    }

    private List<ProductVariant> seedProducts(List<Brand> brands) {
        List<Product> allProducts = new ArrayList<>();
        List<ProductVariant> allVariants = new ArrayList<>();
        List<ProductTranslation> allTranslations = new ArrayList<>();
        List<ProductImage> allImages = new ArrayList<>();
        List<Inventory> allInventory = new ArrayList<>();

        List<List<ProductTemplate>> allTemplates = new ArrayList<>();
        for (Brand brand : brands) {
            List<ProductTemplate> templates = getTemplatesForBrand(brand.getName());
            List<ProductTemplate> expanded = new ArrayList<>(templates);
            expanded.addAll(generateExtraProducts(brand.getName(), templates));
            allTemplates.add(expanded);

            for (ProductTemplate template : expanded) {
                String slug = Product.generateSlug(brand.getSlug(), template.nameEn);
                BigDecimal priceUsd = template.priceUsd;
                BigDecimal priceKrw = priceUsd.multiply(USD_TO_KRW).setScale(0, RoundingMode.HALF_UP);
                BigDecimal priceJpy = priceUsd.multiply(USD_TO_JPY).setScale(0, RoundingMode.HALF_UP);

                Product product = Product.create(
                        brand, slug, template.category, template.era,
                        priceUsd, "USD", priceUsd, priceKrw, priceJpy,
                        template.fabricWeightOz, template.fabricType, template.fabricWeave
                );
                allProducts.add(product);
            }
        }

        productRepository.saveAll(allProducts);
        entityManager.flush();

        int productIndex = 0;
        for (int b = 0; b < brands.size(); b++) {
            List<ProductTemplate> expanded = allTemplates.get(b);
            for (ProductTemplate template : expanded) {
                Product product = allProducts.get(productIndex);

                allTranslations.add(ProductTranslation.create(product, "en", template.nameEn, template.descEn));
                allTranslations.add(ProductTranslation.create(product, "ko", template.nameKo, template.descKo));
                allTranslations.add(ProductTranslation.create(product, "ja", template.nameJa, template.descJa));

                int imageCount = 1 + rand(2);
                for (int i = 0; i < imageCount; i++) {
                    String url = String.format("https://cdn.foundry.com/products/%s/%d.jpg", product.getSlug(), i + 1);
                    allImages.add(ProductImage.create(product, url, (short) i, i == 0));
                }

                String[] sizes = getSizesForCategory(template.category);
                String[][] colors = template.colors;
                for (String[] color : colors) {
                    for (String size : sizes) {
                        String sku = String.format("SKU-%06d", skuCounter++);
                        Measurements m = getMeasurements(template.category, size);
                        ProductVariant variant = ProductVariant.create(
                                product, sku, size, color[0], color[1],
                                null, null,
                                m.chest, m.shoulder, m.sleeve, m.bodyLength,
                                m.waist, m.inseam, m.thigh, m.hem
                        );
                        allVariants.add(variant);
                    }
                }
                productIndex++;
            }
        }

        productTranslationRepository.saveAll(allTranslations);
        productImageRepository.saveAll(allImages);
        productVariantRepository.saveAll(allVariants);
        entityManager.flush();

        for (ProductVariant variant : allVariants) {
            Inventory inv = Inventory.create(variant.getId());
            inv.adjust(20 + rand(81));
            allInventory.add(inv);
        }
        inventoryRepository.saveAll(allInventory);
        entityManager.flush();

        log.info("Seeded {} products, {} variants, {} translations, {} images, {} inventory records",
                allProducts.size(), allVariants.size(), allTranslations.size(),
                allImages.size(), allInventory.size());
        return allVariants;
    }

    private List<ProductTemplate> generateExtraProducts(String brandName, List<ProductTemplate> baseTemplates) {
        List<ProductTemplate> extras = new ArrayList<>();
        String[][] suffixSets = {
                {"One Wash", "ワンウォッシュ", "원워시"},
                {"Raw", "生デニム", "로우"},
                {"Black", "ブラック", "블랙"},
                {"Double Indigo", "ダブルインディゴ", "더블 인디고"},
                {"Overdyed", "オーバーダイ", "오버다이드"},
                {"Faded", "フェード加工", "페이디드"},
                {"LTD Edition", "限定版", "한정판"},
                {"Blanket Lined", "ブランケットライニング", "블랭킷 안감"},
                {"Heavyweight", "ヘビーウェイト", "헤비웨이트"},
                {"Lightweight", "ライトウェイト", "라이트웨이트"},
                {"Waxed", "ワックス加工", "왁스"},
                {"Selvage", "セルビッジ", "셀비지"},
                {"Vintage Wash", "ヴィンテージウォッシュ", "빈티지 워시"},
                {"Broken Twill", "ブロークンツイル", "브로큰 트윌"},
                {"Sanforized", "サンフォライズド", "산포라이즈드"},
                {"Unsanforized", "未防縮", "미방축"},
                {"Left Hand Twill", "左綾", "레프트 핸드 트윌"},
                {"Natural Indigo", "天然藍", "천연 인디고"},
                {"Sashiko Stitch", "刺し子ステッチ", "사시코 스티치"},
                {"Canvas", "キャンバス", "캔버스"},
                {"Lined", "裏地付き", "안감"},
                {"Slim Cut", "スリムカット", "슬림 컷"},
                {"Wide Cut", "ワイドカット", "와이드 컷"},
                {"Relaxed Fit", "リラックスフィット", "릴랙스드 핏"},
                {"Tapered", "テーパード", "테이퍼드"},
                {"Boot Cut", "ブーツカット", "부트 컷"},
                {"Cropped", "クロップド", "크롭트"},
                {"High Rise", "ハイライズ", "하이라이즈"},
                {"Narrow", "ナロー", "나로우"},
                {"Regular", "レギュラー", "레귤러"},
                {"Lot 2", "Lot 2", "Lot 2"},
                {"Lot 3", "Lot 3", "Lot 3"},
                {"MK II", "MK II", "MK II"},
                {"MK III", "MK III", "MK III"},
                {"SS Edition", "SS エディション", "SS 에디션"},
                {"FW Edition", "FW エディション", "FW 에디션"},
        };

        int needed = 34;
        int suffixIndex = 0;
        for (int i = 0; suffixIndex < suffixSets.length && extras.size() < needed; i++) {
            ProductTemplate base = baseTemplates.get(i % baseTemplates.size());
            String[] suffix = suffixSets[suffixIndex];
            suffixIndex++;

            BigDecimal priceDelta = bd(String.valueOf(-30 + rand(80)));
            BigDecimal newPrice = base.priceUsd.add(priceDelta);
            if (newPrice.compareTo(bd("25")) < 0) newPrice = bd("25");

            BigDecimal weight = base.fabricWeightOz;
            if (weight != null) {
                weight = weight.add(bd(String.valueOf(-2 + rand(5)))).max(bd("6.0"));
            }

            String era = ERAS[rand(ERAS.length)];
            String[][] newColors = base.colors.length > 0
                    ? new String[][]{base.colors[rand(base.colors.length)]}
                    : base.colors;

            extras.add(new ProductTemplate(
                    base.nameEn + " " + suffix[0],
                    base.category, era, newPrice,
                    weight, base.fabricType, base.fabricWeave,
                    base.nameKo + " " + suffix[2],
                    base.descKo + " " + suffix[2] + " ver.",
                    base.nameJa + " " + suffix[1],
                    base.descJa + " " + suffix[1] + "バージョン",
                    base.descEn + " — " + suffix[0] + " version",
                    newColors
            ));
        }
        return extras;
    }

    private void seedDropEvents(List<ProductVariant> allVariants) {
        LocalDateTime now = LocalDateTime.now();

        DropEvent drop1 = DropEvent.create(
                "Iron Heart 25oz Selvedge Collection",
                "Limited release of Iron Heart's legendary 25oz extra-heavy selvedge denim line",
                now.plusDays(7), now.plusDays(14));

        DropEvent drop2 = DropEvent.create(
                "Real McCoy's WWII Flight Jacket Reissue",
                "Faithful reproduction of the iconic Type A-2 flight jacket using horsehide leather",
                now.minusDays(2), now.plusDays(5));

        DropEvent drop3 = DropEvent.create(
                "Buzz Rickson's x William Gibson Capsule",
                "Collaborative capsule collection inspired by speculative military prototypes",
                now.minusDays(5), now.plusDays(2));

        DropEvent drop4 = DropEvent.create(
                "Kapital Indigo Sashiko Festival",
                "Hand-stitched sashiko garments dyed with natural Tokushima indigo",
                now.plusDays(14), now.plusDays(21));

        DropEvent drop5 = DropEvent.create(
                "Filson x Foundry Heritage Pack",
                "Exclusive Foundry collaboration featuring Filson's legendary Mackinaw Wool",
                now.minusDays(10), now.minusDays(3));

        List<DropEvent> drops = dropEventRepository.saveAll(List.of(drop1, drop2, drop3, drop4, drop5));
        entityManager.flush();

        drops.get(1).transitionTo("OPEN");
        drops.get(2).transitionTo("OPEN");
        drops.get(2).transitionTo("SELLING");
        drops.get(4).transitionTo("OPEN");
        drops.get(4).transitionTo("SELLING");
        drops.get(4).transitionTo("SOLD_OUT");
        drops.get(4).transitionTo("CLOSED");
        dropEventRepository.saveAll(drops);
        entityManager.flush();

        List<DropProduct> dropProducts = new ArrayList<>();
        int variantCount = allVariants.size();
        for (DropEvent drop : drops) {
            int numProducts = 5 + rand(6);
            for (int i = 0; i < numProducts; i++) {
                ProductVariant variant = allVariants.get(rand(variantCount));
                int allocated = 10 + rand(41);
                BigDecimal dropPrice = new BigDecimal(100 + rand(400)).setScale(2, RoundingMode.HALF_UP);
                dropProducts.add(DropProduct.create(drop, variant.getId(), allocated, dropPrice, "USD"));
            }
        }
        dropProductRepository.saveAll(dropProducts);
        log.info("Seeded {} drop events with {} drop products", drops.size(), dropProducts.size());
    }

    // --- Product template data ---

    private List<ProductTemplate> getTemplatesForBrand(String brandName) {
        return switch (brandName) {
            case "Iron Heart" -> ironHeartProducts();
            case "The Real McCoy's" -> realMcCoysProducts();
            case "Buzz Rickson's" -> buzzRicksonsProducts();
            case "Warehouse & Co." -> warehouseProducts();
            case "The Flat Head" -> flatHeadProducts();
            case "Studio D'Artisan" -> studioDartisanProducts();
            case "Sugar Cane" -> sugarCaneProducts();
            case "Mister Freedom" -> misterFreedomProducts();
            case "Filson" -> filsonProducts();
            case "Red Wing" -> redWingProducts();
            case "Visvim" -> visvimProducts();
            case "Kapital" -> kapitalProducts();
            default -> List.of();
        };
    }

    private List<ProductTemplate> ironHeartProducts() {
        return List.of(
                pt("IH-634S 21oz Straight", "DENIM", "1950s_WORKWEAR", bd("375"),
                        bd("21.0"), "DENIM", "SELVEDGE",
                        "IH-634S 21oz 셀비지 스트레이트 진", "아이언하트 시그니처 21온스 헤비 셀비지 데님 스트레이트 핏",
                        "IH-634S 21oz セルビッジストレート", "アイアンハート定番21ozヘビーセルビッジデニム ストレートフィット",
                        "Signature 21oz heavy selvedge denim in a classic straight fit with hidden rivets",
                        cc("Indigo", "#1a237e")),
                pt("IH-888S 21oz High Rise Tapered", "DENIM", "1950s_WORKWEAR", bd("375"),
                        bd("21.0"), "DENIM", "SELVEDGE",
                        "IH-888S 21oz 하이라이즈 테이퍼드 진", "아이언하트 21온스 셀비지 데님 하이라이즈 릴랙스 테이퍼드",
                        "IH-888S 21oz ハイライズテーパード", "21ozセルビッジデニム ハイライズリラックステーパード",
                        "High rise relaxed tapered fit in 21oz selvedge denim with chain-stitch hem",
                        cc("Indigo", "#1a237e")),
                pt("IH-526J 21oz Type III Jacket", "JACKET", "1950s_WORKWEAR", bd("495"),
                        bd("21.0"), "DENIM", "SELVEDGE",
                        "IH-526J 21oz 타입 III 재킷", "아이언하트 21온스 셀비지 데님 타입3 재킷",
                        "IH-526J 21oz タイプIIIジャケット", "21ozセルビッジデニム タイプ3ジャケット",
                        "Type III denim jacket in 21oz selvedge with copper buttons and pleated back",
                        cc("Indigo", "#1a237e")),
                pt("IH-777S-142 14oz Slim Tapered", "DENIM", "1960s_AMERICANA", bd("330"),
                        bd("14.0"), "DENIM", "SELVEDGE",
                        "IH-777S-142 14oz 슬림 테이퍼드", "아이언하트 14온스 셀비지 슬림 테이퍼드",
                        "IH-777S-142 14oz スリムテーパード", "14ozセルビッジ スリムテーパードフィット",
                        "Slim tapered fit in 14oz medium-weight selvedge with aggressive taper below knee",
                        cc("Indigo", "#1a237e")),
                pt("IH-25oz Super Heavy", "DENIM", "1950s_WORKWEAR", bd("425"),
                        bd("25.0"), "DENIM", "SELVEDGE",
                        "IH 25oz 슈퍼 헤비 셀비지", "아이언하트 25온스 초중량 셀비지 데님",
                        "IH 25oz スーパーヘビーセルビッジ", "25oz超ヘビーウェイトセルビッジデニム",
                        "Ultra-heavyweight 25oz selvedge denim for dedicated faders",
                        cc("Indigo", "#1a237e")),
                pt("IHSH-232 Western Flannel", "SHIRT", "1950s_WORKWEAR", bd("295"),
                        bd("10.0"), "FLANNEL", "TWILL",
                        "IHSH-232 웨스턴 플란넬 셔츠", "아이언하트 울트라 헤비 플란넬 웨스턴 셔츠",
                        "IHSH-232 ウエスタンフランネル", "ウルトラヘビーフランネル ウエスタンシャツ",
                        "Ultra-heavy flannel western shirt with snaps and gusseted armpits",
                        cc("Red Check", "#b71c1c"), cc("Black Check", "#212121"), cc("Grey Check", "#616161")),
                pt("IHSH-289 Chambray Work Shirt", "SHIRT", "1940s_MILITARY", bd("265"),
                        null, "CHAMBRAY", "PLAIN",
                        "IHSH-289 샴브레이 워크셔츠", "아이언하트 셀비지 샴브레이 워크셔츠",
                        "IHSH-289 シャンブレーワークシャツ", "セルビッジシャンブレー ワークシャツ",
                        "Selvedge chambray work shirt with cat's eye buttons and triple needle stitching",
                        cc("Blue", "#1565c0")),
                pt("IHV-35 Heavy Flannel Vest", "OUTERWEAR", "1950s_WORKWEAR", bd("245"),
                        bd("10.0"), "FLANNEL", "TWILL",
                        "IHV-35 헤비 플란넬 베스트", "아이언하트 울트라 헤비 플란넬 베스트",
                        "IHV-35 ヘビーフランネルベスト", "ウルトラヘビーフランネル ベスト",
                        "Ultra-heavy flannel vest with snap front closure",
                        cc("Navy", "#0d47a1"), cc("Red", "#b71c1c")),
                pt("IHJ-69 Rider's Jacket", "JACKET", "1960s_AMERICANA", bd("650"),
                        null, "LEATHER", "PLAIN",
                        "IHJ-69 라이더스 재킷", "아이언하트 호스하이드 라이더스 재킷",
                        "IHJ-69 ライダースジャケット", "ホースハイド ライダースジャケット",
                        "Horsehide double rider's jacket with YKK brass hardware",
                        cc("Black", "#000000")),
                pt("IH Leather Belt 1.5in", "ACCESSORY", "1950s_WORKWEAR", bd("175"),
                        null, "LEATHER", "PLAIN",
                        "아이언하트 레더 벨트 1.5인치", "아이언하트 풀그레인 레더 벨트",
                        "IH レザーベルト 1.5インチ", "フルグレインレザーベルト",
                        "Full-grain leather belt with heavy brass buckle",
                        cc("Brown", "#4e342e"), cc("Black", "#212121")),
                pt("IHK-030 Waffle Knit Thermal", "KNIT", "1950s_WORKWEAR", bd("185"),
                        null, "COTTON", "PLAIN",
                        "IHK-030 와플 니트 서말", "아이언하트 헤비웨이트 와플 니트 서말 셔츠",
                        "IHK-030 ワッフルニットサーマル", "ヘビーウェイト ワッフルニットサーマルシャツ",
                        "Heavyweight waffle-knit thermal with extended body length",
                        cc("White", "#fafafa"), cc("Black", "#212121")),
                pt("IHP-6 Heavy Duck Pants", "PANTS", "1940s_MILITARY", bd("310"),
                        null, "CANVAS", "TWILL",
                        "IHP-6 헤비 덕 팬츠", "아이언하트 헤비 덕 캔버스 워크 팬츠",
                        "IHP-6 ヘビーダックパンツ", "ヘビーダックキャンバス ワークパンツ",
                        "Heavy duck canvas work pants with double-knee reinforcement",
                        cc("Brown", "#5d4037"), cc("Black", "#212121"))
        );
    }

    private List<ProductTemplate> realMcCoysProducts() {
        return List.of(
                pt("Type A-2 Flight Jacket", "JACKET", "1940s_MILITARY", bd("1200"),
                        null, "LEATHER", "PLAIN",
                        "타입 A-2 비행 재킷", "리얼맥코이 호스하이드 A-2 비행 재킷",
                        "タイプA-2 フライトジャケット", "ホースハイドA-2フライトジャケット",
                        "Horsehide A-2 flight jacket reproduced from original WWII specs",
                        cc("Seal Brown", "#3e2723")),
                pt("N-1 Deck Jacket", "OUTERWEAR", "1940s_MILITARY", bd("895"),
                        null, "COTTON", "PLAIN",
                        "N-1 데크 재킷", "리얼맥코이 N-1 데크 재킷 알파카 안감",
                        "N-1 デッキジャケット", "N-1デッキジャケット アルパカライニング",
                        "N-1 deck jacket with alpaca pile lining and hand-oiled finish",
                        cc("Navy", "#0d47a1"), cc("Khaki", "#827717")),
                pt("Military Chambray Shirt", "SHIRT", "1940s_MILITARY", bd("295"),
                        null, "CHAMBRAY", "PLAIN",
                        "밀리터리 샴브레이 셔츠", "리얼맥코이 밀리터리 샴브레이 셔츠 유심 스타일",
                        "ミリタリーシャンブレーシャツ", "ミリタリーシャンブレーシャツ USMCスタイル",
                        "USMC-style chambray shirt with period-correct stencil and gas flap",
                        cc("Blue", "#1565c0")),
                pt("M-65 Field Jacket", "OUTERWEAR", "1960s_AMERICANA", bd("795"),
                        null, "COTTON", "TWILL",
                        "M-65 필드 재킷", "리얼맥코이 M-65 필드 재킷",
                        "M-65 フィールドジャケット", "M-65フィールドジャケット",
                        "Reproduction M-65 field jacket with original spec OG-107 cotton sateen",
                        cc("Olive Drab", "#33691e")),
                pt("Loopwheel Sweatshirt", "KNIT", "1950s_WORKWEAR", bd("245"),
                        null, "COTTON", "PLAIN",
                        "루프휠 스웨트셔츠", "리얼맥코이 루프휠 스웨트셔츠",
                        "ループウィール スウェットシャツ", "ループウィール スウェットシャツ",
                        "Loopwheel-knit crewneck sweatshirt on vintage Tompkins machines",
                        cc("Heather Grey", "#9e9e9e"), cc("Navy", "#0d47a1")),
                pt("MA-1 Flight Jacket", "JACKET", "1950s_WORKWEAR", bd("750"),
                        null, "COTTON", "SATIN",
                        "MA-1 비행 재킷", "리얼맥코이 MA-1 비행 재킷",
                        "MA-1 フライトジャケット", "MA-1フライトジャケット",
                        "MIL-J-8279E spec MA-1 intermediate flight jacket with orange reversible lining",
                        cc("Sage Green", "#689f38")),
                pt("Joe McCoy Ball Cap", "ACCESSORY", "1950s_WORKWEAR", bd("85"),
                        null, "COTTON", "TWILL",
                        "조 맥코이 볼 캡", "리얼맥코이 빈티지 볼 캡",
                        "ジョーマッコイ ボールキャップ", "ヴィンテージ ボールキャップ",
                        "Vintage-style ball cap with adjustable leather strap",
                        cc("Navy", "#0d47a1"), cc("Khaki", "#827717")),
                pt("Joe McCoy Lot 991 Jeans", "DENIM", "1950s_WORKWEAR", bd("395"),
                        bd("13.5"), "DENIM", "SELVEDGE",
                        "조 맥코이 Lot 991 진", "리얼맥코이 13.5온스 셀비지 데님 스트레이트 레그",
                        "ジョーマッコイ Lot 991", "13.5ozセルビッジデニム ストレートレッグ",
                        "13.5oz deadstock-style selvedge denim with hidden rivets and painted arcs",
                        cc("Indigo", "#1a237e")),
                pt("Buco J-100 Jacket", "JACKET", "1950s_WORKWEAR", bd("1400"),
                        null, "LEATHER", "PLAIN",
                        "부코 J-100 재킷", "리얼맥코이 부코 호스하이드 싱글 라이더스",
                        "Buco J-100 ジャケット", "Buco ホースハイド シングルライダース",
                        "Horsehide single rider's jacket based on the legendary Buco J-100",
                        cc("Black", "#000000")),
                pt("RMC Henley Tee", "KNIT", "1950s_WORKWEAR", bd("95"),
                        null, "COTTON", "PLAIN",
                        "RMC 헨리 티", "리얼맥코이 튜브 니트 헨리넥 티",
                        "RMC ヘンリーネックTシャツ", "チューブニット ヘンリーネックTシャツ",
                        "Tubular-knit henley tee with reinforced placket and flatlock seams",
                        cc("White", "#fafafa"), cc("Black", "#212121"))
        );
    }

    private List<ProductTemplate> buzzRicksonsProducts() {
        return List.of(
                pt("B-15C Flight Jacket", "JACKET", "1940s_MILITARY", bd("695"),
                        null, "COTTON", "TWILL",
                        "B-15C 비행 재킷", "버즈릭슨 B-15C 인터미디엇 비행 재킷",
                        "B-15C フライトジャケット", "B-15C インターミディエイトフライトジャケット",
                        "B-15C intermediate flying jacket with mouton collar and spec knit cuffs",
                        cc("Olive Drab", "#33691e")),
                pt("Type B-6 Sheepskin Jacket", "OUTERWEAR", "1940s_MILITARY", bd("1600"),
                        null, "LEATHER", "PLAIN",
                        "타입 B-6 시프스킨 재킷", "버즈릭슨 시프스킨 B-6 비행 재킷",
                        "タイプB-6 シープスキンジャケット", "シープスキンB-6フライトジャケット",
                        "Sheepskin B-6 flying jacket for extreme high-altitude missions",
                        cc("Seal Brown", "#3e2723")),
                pt("William Gibson MA-1 Black", "JACKET", "1960s_AMERICANA", bd("595"),
                        null, "COTTON", "SATIN",
                        "윌리엄 깁슨 MA-1 블랙", "버즈릭슨 x 윌리엄 깁슨 MA-1",
                        "ウィリアム・ギブソン MA-1 ブラック", "バズリクソンズ×ウィリアム・ギブソン MA-1",
                        "Speculative Black MA-1 from the William Gibson collection with stealth details",
                        cc("Black", "#000000")),
                pt("BR-15330 Sweatshirt", "KNIT", "1940s_MILITARY", bd("195"),
                        null, "COTTON", "PLAIN",
                        "BR-15330 스웨트셔츠", "버즈릭슨 밀리터리 스웨트셔츠",
                        "BR-15330 スウェットシャツ", "ミリタリースウェットシャツ",
                        "Set-in sleeve military sweatshirt on loopwheel machines",
                        cc("Heather Grey", "#9e9e9e"), cc("Navy", "#1a237e")),
                pt("BR-02L Tanker Jacket", "JACKET", "1940s_MILITARY", bd("495"),
                        null, "COTTON", "TWILL",
                        "BR-02L 탱커 재킷", "버즈릭슨 탱커 재킷",
                        "BR-02L タンカースジャケット", "タンカースジャケット",
                        "Cotton tanker jacket with alpaca wool lining for armored crew",
                        cc("Olive Drab", "#33691e")),
                pt("L-2B Flight Jacket", "JACKET", "1950s_WORKWEAR", bd("545"),
                        null, "COTTON", "SATIN",
                        "L-2B 비행 재킷", "버즈릭슨 L-2B 라이트 플라이트 재킷",
                        "L-2B フライトジャケット", "L-2B ライトフライトジャケット",
                        "Lightweight L-2B flight jacket in spec flight satin with knit collar",
                        cc("Sage Green", "#689f38")),
                pt("N-3B Snorkel Parka", "OUTERWEAR", "1950s_WORKWEAR", bd("850"),
                        null, "COTTON", "TWILL",
                        "N-3B 스노클 파카", "버즈릭슨 N-3B 헤비 존 파카",
                        "N-3B スノーケルパーカ", "N-3B ヘビーゾーンパーカ",
                        "N-3B heavy-zone parka with coyote fur trimmed snorkel hood",
                        cc("Sage Green", "#689f38")),
                pt("Military Cargo Pants", "PANTS", "1940s_MILITARY", bd("275"),
                        null, "COTTON", "TWILL",
                        "밀리터리 카고 팬츠", "버즈릭슨 M-43 HBT 카고 팬츠",
                        "ミリタリーカーゴパンツ", "M-43 HBTカーゴパンツ",
                        "M-43 HBT cargo pants with 13-star buttons and period stitching",
                        cc("Olive Drab", "#33691e")),
                pt("AN-6540 Aviator Sunglasses", "ACCESSORY", "1940s_MILITARY", bd("195"),
                        null, null, null,
                        "AN-6540 비행사 선글라스", "버즈릭슨 AN-6540 에비에이터 선글라스",
                        "AN-6540 アビエーターサングラス", "AN-6540 アビエーターサングラス",
                        "Reproduction AN-6540 aviator sunglasses with bayonet temples",
                        cc("Gold", "#f9a825"))
        );
    }

    private List<ProductTemplate> warehouseProducts() {
        return List.of(
                pt("Lot 800 Straight Jeans", "DENIM", "1950s_WORKWEAR", bd("295"),
                        bd("13.5"), "DENIM", "SELVEDGE",
                        "Lot 800 스트레이트 진", "웨어하우스 13.5온스 셀비지 스트레이트 진",
                        "Lot 800 ストレートジーンズ", "13.5ozセルビッジ ストレートジーンズ",
                        "1950s-style straight leg in 13.5oz Memphis cotton selvedge denim",
                        cc("Indigo", "#1a237e")),
                pt("Lot 900 Slim Jeans", "DENIM", "1960s_AMERICANA", bd("295"),
                        bd("13.5"), "DENIM", "SELVEDGE",
                        "Lot 900 슬림 진", "웨어하우스 셀비지 슬림핏 데님",
                        "Lot 900 スリムジーンズ", "セルビッジ スリムフィットデニム",
                        "Slim fit selvedge denim inspired by 1960s youth culture silhouettes",
                        cc("Indigo", "#1a237e")),
                pt("2nd-Hand Series 1001XX", "DENIM", "1950s_WORKWEAR", bd("345"),
                        bd("14.0"), "DENIM", "SELVEDGE",
                        "세컨드핸드 시리즈 1001XX", "웨어하우스 배너 데님 1001XX",
                        "セカンドハンドシリーズ 1001XX", "バナーデニム 1001XX",
                        "Banner denim woven on vintage shuttle looms with natural indigo hue",
                        cc("Indigo", "#1a237e")),
                pt("Duck Digger Chinos", "PANTS", "1940s_MILITARY", bd("225"),
                        null, "COTTON", "TWILL",
                        "덕 디거 치노", "웨어하우스 빈티지 밀리터리 치노 팬츠",
                        "ダックディガーチノ", "ヴィンテージミリタリーチノパンツ",
                        "Vintage military chinos with west-point fabric and wide legs",
                        cc("Khaki", "#827717"), cc("Olive", "#33691e")),
                pt("Warehouse Flannel Check", "SHIRT", "1950s_WORKWEAR", bd("215"),
                        bd("8.0"), "FLANNEL", "TWILL",
                        "웨어하우스 플란넬 체크 셔츠", "웨어하우스 빈티지 플란넬 체크 셔츠",
                        "ウエアハウス フランネルチェック", "ヴィンテージフランネル チェックシャツ",
                        "Vintage-weight flannel check shirt with elbow gussets",
                        cc("Red Check", "#c62828"), cc("Blue Check", "#1565c0")),
                pt("Loopwheel Pocket Tee", "KNIT", "1950s_WORKWEAR", bd("85"),
                        null, "COTTON", "PLAIN",
                        "루프휠 포켓 티", "웨어하우스 루프휠 포켓 티셔츠",
                        "ループウィールポケットTシャツ", "ループウィールポケットTシャツ",
                        "Tubular-knit pocket tee with bar-tacked chest pocket",
                        cc("White", "#fafafa"), cc("Navy", "#0d47a1"), cc("Black", "#212121")),
                pt("WH Denim Jacket 2nd Type", "JACKET", "1950s_WORKWEAR", bd("345"),
                        bd("13.5"), "DENIM", "SELVEDGE",
                        "WH 데님 재킷 세컨드 타입", "웨어하우스 셀비지 데님 세컨드 타입 재킷",
                        "WH デニムジャケット 2ndタイプ", "セルビッジデニム 2ndタイプジャケット",
                        "2nd-type denim jacket in 13.5oz selvedge with pleated front",
                        cc("Indigo", "#1a237e")),
                pt("WH-1105 Work Shirt", "SHIRT", "1940s_MILITARY", bd("195"),
                        null, "CHAMBRAY", "PLAIN",
                        "WH-1105 워크셔츠", "웨어하우스 헤비 샴브레이 워크셔츠",
                        "WH-1105 ワークシャツ", "ヘビーシャンブレーワークシャツ",
                        "Triple-stitched chambray work shirt with V-stitch reinforcement",
                        cc("Blue", "#1565c0")),
                pt("WH Canvas Tote Bag", "ACCESSORY", "1950s_WORKWEAR", bd("125"),
                        null, "CANVAS", "PLAIN",
                        "WH 캔버스 토트백", "웨어하우스 파라핀 캔버스 토트백",
                        "WH キャンバストートバッグ", "パラフィンキャンバストートバッグ",
                        "Paraffin-waxed canvas tote bag with leather handles",
                        cc("Natural", "#d7ccc8"), cc("Navy", "#0d47a1"))
        );
    }

    private List<ProductTemplate> flatHeadProducts() {
        return List.of(
                pt("FH 3001 14.5oz Straight", "DENIM", "1950s_WORKWEAR", bd("345"),
                        bd("14.5"), "DENIM", "SELVEDGE",
                        "FH 3001 14.5oz 스트레이트", "더 플랫헤드 시그니처 14.5온스 스트레이트",
                        "FH 3001 14.5oz ストレート", "ザ・フラットヘッド定番14.5ozストレート",
                        "Signature straight fit in proprietary 14.5oz Zimbabwe cotton selvedge",
                        cc("Indigo", "#1a237e")),
                pt("FH 3002 14.5oz Slim", "DENIM", "1960s_AMERICANA", bd("345"),
                        bd("14.5"), "DENIM", "SELVEDGE",
                        "FH 3002 14.5oz 슬림", "더 플랫헤드 14.5온스 슬림 테이퍼드",
                        "FH 3002 14.5oz スリム", "14.5oz スリムテーパード",
                        "Slim tapered fit in 14.5oz Zimbabwe cotton with aggressive fading character",
                        cc("Indigo", "#1a237e")),
                pt("FN-THC-025 Heavyweight Tee", "KNIT", "1950s_WORKWEAR", bd("95"),
                        null, "COTTON", "PLAIN",
                        "FN-THC-025 헤비웨이트 티", "더 플랫헤드 헤비웨이트 코튼 티셔츠",
                        "FN-THC-025 ヘビーウェイトTシャツ", "ヘビーウェイトコットンTシャツ",
                        "Heavy cotton tee with flat seams and reinforced collar",
                        cc("White", "#fafafa"), cc("Black", "#212121"), cc("Navy", "#0d47a1")),
                pt("Western Flannel Shirt", "SHIRT", "1950s_WORKWEAR", bd("275"),
                        bd("9.0"), "FLANNEL", "TWILL",
                        "웨스턴 플란넬 셔츠", "더 플랫헤드 네이티브 패턴 웨스턴 플란넬",
                        "ウエスタンフランネルシャツ", "ネイティブパターン ウエスタンフランネル",
                        "Native-pattern western flannel with sawtooth pockets",
                        cc("Red Pattern", "#c62828"), cc("Blue Pattern", "#1565c0")),
                pt("FH Stockburg Boots", "FOOTWEAR", "1950s_WORKWEAR", bd("595"),
                        null, "LEATHER", "PLAIN",
                        "FH 스톡버그 부츠", "더 플랫헤드 스톡버그 엔지니어 부츠",
                        "FH ストックバーグブーツ", "ストックバーグ エンジニアブーツ",
                        "Engineer boots with heavyweight horsehide and Goodyear welt construction",
                        cc("Black", "#000000"), cc("Brown", "#4e342e")),
                pt("FH Leather Wallet Long", "ACCESSORY", "1950s_WORKWEAR", bd("350"),
                        null, "LEATHER", "PLAIN",
                        "FH 레더 롱 월렛", "더 플랫헤드 코도반 롱 월렛",
                        "FH レザーロングウォレット", "コードバン ロングウォレット",
                        "Shell cordovan long wallet with hand-stitched edges",
                        cc("Black", "#000000"), cc("Burgundy", "#880e4f")),
                pt("FH-5002 Type III Jacket", "JACKET", "1950s_WORKWEAR", bd("395"),
                        bd("14.5"), "DENIM", "SELVEDGE",
                        "FH-5002 타입 III 재킷", "더 플랫헤드 14.5온스 타입3 데님 재킷",
                        "FH-5002 タイプIIIジャケット", "14.5oz タイプ3デニムジャケット",
                        "Type III jacket in proprietary 14.5oz selvedge with hand-distressed details",
                        cc("Indigo", "#1a237e")),
                pt("FH Pioneer Trousers", "PANTS", "1940s_MILITARY", bd("265"),
                        null, "COTTON", "TWILL",
                        "FH 파이오니어 트라우저", "더 플랫헤드 파이오니어 워크 트라우저",
                        "FH パイオニアトラウザーズ", "パイオニアワークトラウザーズ",
                        "Wide-leg work trousers with period-correct cinch back",
                        cc("Khaki", "#827717"), cc("Brown", "#5d4037"))
        );
    }

    private List<ProductTemplate> studioDartisanProducts() {
        return List.of(
                pt("SD-108 15oz Straight", "DENIM", "1950s_WORKWEAR", bd("295"),
                        bd("15.0"), "DENIM", "SELVEDGE",
                        "SD-108 15oz 스트레이트", "스튜디오 다르치잔 15온스 셀비지 스트레이트",
                        "SD-108 15oz ストレート", "15ozセルビッジストレート",
                        "Classic straight fit in 15oz right-hand twill selvedge with pig logo",
                        cc("Indigo", "#1a237e")),
                pt("SD-103 15oz Tapered", "DENIM", "1960s_AMERICANA", bd("295"),
                        bd("15.0"), "DENIM", "SELVEDGE",
                        "SD-103 15oz 테이퍼드", "스튜디오 다르치잔 15온스 테이퍼드",
                        "SD-103 15oz テーパード", "15ozセルビッジテーパード",
                        "Tapered silhouette in 15oz selvedge with distinctive arc stitching",
                        cc("Indigo", "#1a237e")),
                pt("Sashiko Work Jacket", "JACKET", "1950s_WORKWEAR", bd("395"),
                        null, "COTTON", "PLAIN",
                        "사시코 워크 재킷", "스튜디오 다르치잔 사시코 스티치 워크 재킷",
                        "刺し子ワークジャケット", "刺し子ステッチ ワークジャケット",
                        "Sashiko-stitched work jacket with hand-finished indigo dye",
                        cc("Indigo", "#1a237e")),
                pt("Kasezome Natural Dyed Tee", "KNIT", "1970s_VINTAGE", bd("110"),
                        null, "COTTON", "PLAIN",
                        "카세조메 내추럴 다이드 티", "스튜디오 다르치잔 풀 염색 티셔츠",
                        "かせ染めナチュラルダイTシャツ", "かせ染めナチュラルダイTシャツ",
                        "Natural plant-dyed tee using traditional kasezome technique",
                        cc("Indigo", "#1a237e"), cc("Persimmon", "#e65100")),
                pt("Crazy Pattern Aloha Shirt", "SHIRT", "1970s_VINTAGE", bd("245"),
                        null, "COTTON", "PLAIN",
                        "크레이지 패턴 알로하 셔츠", "스튜디오 다르치잔 크레이지 패턴 알로하",
                        "クレイジーパターンアロハシャツ", "クレイジーパターンアロハシャツ",
                        "Rayon aloha shirt with original pig-motif crazy pattern",
                        cc("Multi", "#e91e63")),
                pt("SD Selvedge Chambray BD", "SHIRT", "1960s_AMERICANA", bd("215"),
                        null, "CHAMBRAY", "SELVEDGE",
                        "SD 셀비지 샴브레이 BD 셔츠", "스튜디오 다르치잔 셀비지 샴브레이 버튼다운",
                        "SD セルビッジシャンブレーBDシャツ", "セルビッジシャンブレー ボタンダウンシャツ",
                        "Button-down shirt in selvedge chambray with ivy-style collar roll",
                        cc("Blue", "#1565c0")),
                pt("SD Leather Belt", "ACCESSORY", "1950s_WORKWEAR", bd("145"),
                        null, "LEATHER", "PLAIN",
                        "SD 레더 벨트", "스튜디오 다르치잔 오일드 레더 벨트",
                        "SD レザーベルト", "オイルドレザーベルト",
                        "Oil-tanned leather belt with brass pig emblem buckle",
                        cc("Brown", "#4e342e")),
                pt("SD Indigo Canvas Bag", "ACCESSORY", "1950s_WORKWEAR", bd("175"),
                        null, "CANVAS", "PLAIN",
                        "SD 인디고 캔버스 백", "스튜디오 다르치잔 인디고 캔버스 숄더백",
                        "SD インディゴキャンバスバッグ", "インディゴキャンバス ショルダーバッグ",
                        "Indigo-dyed canvas shoulder bag with leather trim",
                        cc("Indigo", "#1a237e")),
                pt("SD Fox Fiber Chinos", "PANTS", "1960s_AMERICANA", bd("245"),
                        null, "COTTON", "TWILL",
                        "SD 폭스 파이버 치노", "스튜디오 다르치잔 폭스 파이버 치노 팬츠",
                        "SD フォックスファイバーチノ", "フォックスファイバーチノパンツ",
                        "Chinos woven from naturally colored fox fiber cotton",
                        cc("Brown", "#6d4c41"), cc("Green", "#558b2f"))
        );
    }

    private List<ProductTemplate> sugarCaneProducts() {
        return List.of(
                pt("SC41947 14.25oz Hawaii", "DENIM", "1950s_WORKWEAR", bd("265"),
                        bd("14.25"), "DENIM", "SELVEDGE",
                        "SC41947 14.25oz 하와이", "슈가케인 14.25oz 셀비지 스트레이트",
                        "SC41947 14.25oz ハワイ", "14.25ozセルビッジ ストレート",
                        "1947 model straight fit in 14.25oz sugar cane fiber blend selvedge",
                        cc("Indigo", "#1a237e")),
                pt("SC41966 Star Jeans", "DENIM", "1960s_AMERICANA", bd("275"),
                        bd("14.0"), "DENIM", "SELVEDGE",
                        "SC41966 스타 진", "슈가케인 14온스 스타 진 슬림핏",
                        "SC41966 スタージーンズ", "14ozスタージーンズ スリムフィット",
                        "1966 model slim fit with star-printed pocket bags",
                        cc("Indigo", "#1a237e")),
                pt("Fiction Romance Work Shirt", "SHIRT", "1940s_MILITARY", bd("195"),
                        null, "COTTON", "PLAIN",
                        "픽션 로맨스 워크셔츠", "슈가케인 픽션 로맨스 워크셔츠",
                        "フィクションロマンスワークシャツ", "フィクションロマンス ワークシャツ",
                        "Pre-war style work shirt with cat's eye buttons and rounded hem",
                        cc("Blue", "#1565c0"), cc("Grey", "#757575")),
                pt("SC Pea Coat", "OUTERWEAR", "1940s_MILITARY", bd("495"),
                        bd("24.0"), "WOOL", "TWILL",
                        "SC 피코트", "슈가케인 네이비 피코트 24온스 멜턴 울",
                        "SC ピーコート", "ネイビーピーコート 24ozメルトンウール",
                        "Navy pea coat in 24oz melton wool with USN-spec anchor buttons",
                        cc("Navy", "#0d47a1")),
                pt("SC Corduroy Trousers", "PANTS", "1970s_VINTAGE", bd("195"),
                        null, "COTTON", "TWILL",
                        "SC 코듀로이 트라우저", "슈가케인 코듀로이 와이드 트라우저",
                        "SC コーデュロイトラウザーズ", "コーデュロイ ワイドトラウザーズ",
                        "Wide-leg corduroy trousers with vintage high rise",
                        cc("Brown", "#5d4037"), cc("Olive", "#33691e")),
                pt("SC Denim Type I Jacket", "JACKET", "1940s_MILITARY", bd("325"),
                        bd("14.25"), "DENIM", "SELVEDGE",
                        "SC 데님 타입 I 재킷", "슈가케인 14.25온스 퍼스트 타입 데님 재킷",
                        "SC デニム タイプIジャケット", "14.25oz ファーストタイプ デニムジャケット",
                        "First-type denim jacket with cinch back and single chest pocket",
                        cc("Indigo", "#1a237e")),
                pt("SC Bandana", "ACCESSORY", "1950s_WORKWEAR", bd("35"),
                        null, "COTTON", "PLAIN",
                        "SC 반다나", "슈가케인 셀비지 엣지 반다나",
                        "SC バンダナ", "セルビッジエッジ バンダナ",
                        "Selvedge-edge bandana with discharge-printed paisley pattern",
                        cc("Red", "#c62828"), cc("Navy", "#0d47a1"), cc("Indigo", "#1a237e")),
                pt("SC Canvas Sneakers", "FOOTWEAR", "1950s_WORKWEAR", bd("125"),
                        null, "CANVAS", "PLAIN",
                        "SC 캔버스 스니커즈", "슈가케인 빈티지 캔버스 스니커즈",
                        "SC キャンバススニーカー", "ヴィンテージキャンバス スニーカー",
                        "Vintage-style canvas sneakers with vulcanized rubber sole",
                        cc("White", "#fafafa"), cc("Black", "#212121"))
        );
    }

    private List<ProductTemplate> misterFreedomProducts() {
        return List.of(
                pt("Lot 64 Californian", "DENIM", "1950s_WORKWEAR", bd("385"),
                        bd("15.0"), "DENIM", "SELVEDGE",
                        "Lot 64 캘리포니안", "미스터 프리덤 15온스 캘리포니안 셀비지 데님",
                        "Lot 64 カリフォルニアン", "15ozカリフォルニアン セルビッジデニム",
                        "Classic Californian fit in 15oz custom-woven selvedge with chainstitch runoff",
                        cc("Indigo", "#1a237e")),
                pt("Midnight Denim Ranch Blouse", "JACKET", "1940s_MILITARY", bd("495"),
                        bd("16.0"), "DENIM", "SELVEDGE",
                        "미드나잇 데님 랜치 블라우스", "미스터 프리덤 16온스 랜치 블라우스",
                        "ミッドナイトデニム ランチブラウス", "16ozデニム ランチブラウス",
                        "Ranch blouse in 16oz midnight selvedge denim with copper rivet reinforcement",
                        cc("Midnight Indigo", "#0d47a1")),
                pt("Trapper Jacket", "OUTERWEAR", "1940s_MILITARY", bd("695"),
                        null, "WOOL", "PLAIN",
                        "트래퍼 재킷", "미스터 프리덤 트래퍼 매키노 재킷",
                        "トラッパージャケット", "トラッパーマキノジャケット",
                        "Mackinaw-style trapper jacket in heavyweight blanket wool",
                        cc("Red Plaid", "#c62828")),
                pt("Vaquero Cowboy Shirt", "SHIRT", "1950s_WORKWEAR", bd("345"),
                        null, "COTTON", "SATIN",
                        "바케로 카우보이 셔츠", "미스터 프리덤 바케로 새틴 카우보이 셔츠",
                        "バケロ カウボーイシャツ", "バケロ サテンカウボーイシャツ",
                        "Embroidered satin cowboy shirt with pearl snaps and smile pockets",
                        cc("Black", "#000000"), cc("Burgundy", "#880e4f")),
                pt("MF Continental Trousers", "PANTS", "1960s_AMERICANA", bd("295"),
                        null, "COTTON", "TWILL",
                        "MF 콘티넨탈 트라우저", "미스터 프리덤 콘티넨탈 슬림 트라우저",
                        "MF コンチネンタルトラウザーズ", "コンチネンタル スリムトラウザーズ",
                        "Continental slim trousers with flap-back pockets and high waist",
                        cc("Khaki", "#827717"), cc("Black", "#212121")),
                pt("Sea Hunt Vest", "OUTERWEAR", "1960s_AMERICANA", bd("245"),
                        null, "COTTON", "PLAIN",
                        "씨 헌트 베스트", "미스터 프리덤 씨 헌트 스타일 베스트",
                        "シーハントベスト", "シーハントスタイル ベスト",
                        "Nautical-inspired vest with patch pockets and contrast piping",
                        cc("Navy", "#0d47a1")),
                pt("MF Sportsman Cap", "ACCESSORY", "1950s_WORKWEAR", bd("75"),
                        null, "COTTON", "TWILL",
                        "MF 스포츠맨 캡", "미스터 프리덤 빈티지 스포츠맨 캡",
                        "MF スポーツマンキャップ", "ヴィンテージ スポーツマンキャップ",
                        "Vintage-style sportsman cap with embroidered crest",
                        cc("Navy", "#0d47a1"), cc("Olive", "#33691e")),
                pt("MF Surplus Cargo Pants", "PANTS", "1940s_MILITARY", bd("325"),
                        null, "COTTON", "TWILL",
                        "MF 서플러스 카고 팬츠", "미스터 프리덤 밀리터리 서플러스 카고 팬츠",
                        "MF サープラスカーゴパンツ", "ミリタリーサープラス カーゴパンツ",
                        "Mil-surplus inspired cargo pants with oversized bellows pockets",
                        cc("Olive Drab", "#33691e")),
                pt("MF Muleskinner Boots", "FOOTWEAR", "1950s_WORKWEAR", bd("545"),
                        null, "LEATHER", "PLAIN",
                        "MF 뮬스키너 부츠", "미스터 프리덤 뮬스키너 로퍼 부츠",
                        "MF ミュールスキナーブーツ", "ミュールスキナー ローパーブーツ",
                        "Roughout leather roper boots with hand-turned soles",
                        cc("Natural", "#d7ccc8"))
        );
    }

    private List<ProductTemplate> filsonProducts() {
        return List.of(
                pt("Mackinaw Cruiser Jacket", "OUTERWEAR", "1950s_WORKWEAR", bd("495"),
                        bd("24.0"), "WOOL", "TWILL",
                        "매키노 크루저 재킷", "필슨 매키노 크루저 24온스 버진 울",
                        "マッキーノクルーザージャケット", "マッキーノクルーザー 24ozバージンウール",
                        "24oz virgin wool cruiser jacket with four front pockets and bi-swing back",
                        cc("Forest Green", "#1b5e20"), cc("Red/Black", "#b71c1c")),
                pt("Tin Cloth Field Jacket", "JACKET", "1950s_WORKWEAR", bd("395"),
                        null, "CANVAS", "PLAIN",
                        "틴 클로스 필드 재킷", "필슨 틴 클로스 필드 재킷",
                        "ティンクロス フィールドジャケット", "ティンクロス フィールドジャケット",
                        "Oil-finish tin cloth field jacket with moleskin-lined collar",
                        cc("Tan", "#a1887f"), cc("Otter Green", "#33691e")),
                pt("Original Briefcase", "ACCESSORY", "1950s_WORKWEAR", bd("325"),
                        null, "CANVAS", "PLAIN",
                        "오리지널 브리프케이스", "필슨 오리지널 트윌 브리프케이스",
                        "オリジナルブリーフケース", "オリジナル ツイルブリーフケース",
                        "Rugged twill briefcase with bridle leather handles and brass hardware",
                        cc("Tan", "#a1887f"), cc("Navy", "#0d47a1"), cc("Otter Green", "#33691e")),
                pt("Alaskan Guide Shirt", "SHIRT", "1950s_WORKWEAR", bd("175"),
                        null, "COTTON", "TWILL",
                        "알래스칸 가이드 셔츠", "필슨 알래스칸 가이드 코튼 셔츠",
                        "アラスカンガイドシャツ", "アラスカンガイド コットンシャツ",
                        "Heavy cotton flannel guide shirt with single-needle tailoring",
                        cc("Red/Black Plaid", "#c62828"), cc("Green/Black Plaid", "#2e7d32")),
                pt("Double Mackinaw Vest", "OUTERWEAR", "1950s_WORKWEAR", bd("275"),
                        bd("24.0"), "WOOL", "TWILL",
                        "더블 매키노 베스트", "필슨 24온스 더블 매키노 울 베스트",
                        "ダブルマッキーノベスト", "24ozダブルマッキーノウール ベスト",
                        "Double-layer 24oz mackinaw wool vest with rib-knit collar",
                        cc("Forest Green", "#1b5e20")),
                pt("Tin Cloth Chaps", "PANTS", "1950s_WORKWEAR", bd("195"),
                        null, "CANVAS", "PLAIN",
                        "틴 클로스 챕스", "필슨 틴 클로스 오일피니시 챕스",
                        "ティンクロスチャップス", "ティンクロス オイルフィニッシュチャップス",
                        "Oil-finished tin cloth chaps for brush and briar protection",
                        cc("Tan", "#a1887f")),
                pt("Medium Duffle Bag", "ACCESSORY", "1950s_WORKWEAR", bd("295"),
                        null, "CANVAS", "PLAIN",
                        "미디엄 더플 백", "필슨 러기드 트윌 미디엄 더플 백",
                        "ミディアムダッフルバッグ", "ラギッドツイル ミディアムダッフルバッグ",
                        "Rugged twill medium duffle with storm-flap zipper closure",
                        cc("Tan", "#a1887f"), cc("Navy", "#0d47a1")),
                pt("Mackinaw Wool Cap", "ACCESSORY", "1950s_WORKWEAR", bd("65"),
                        null, "WOOL", "TWILL",
                        "매키노 울 캡", "필슨 매키노 울 이어플랩 캡",
                        "マッキーノウールキャップ", "マッキーノウール イヤーフラップキャップ",
                        "Mackinaw wool cap with fold-down earflaps",
                        cc("Forest Green", "#1b5e20"), cc("Red", "#c62828")),
                pt("Tin Cloth Short Lined Cruiser", "OUTERWEAR", "1950s_WORKWEAR", bd("450"),
                        null, "CANVAS", "PLAIN",
                        "틴 클로스 쇼트 라인드 크루저", "필슨 틴 클로스 안감 크루저 재킷",
                        "ティンクロス ショートラインドクルーザー", "ティンクロス ライニングクルーザージャケット",
                        "Short-length tin cloth cruiser with dry-finish and wool lining",
                        cc("Tan", "#a1887f"))
        );
    }

    private List<ProductTemplate> redWingProducts() {
        return List.of(
                pt("875 Classic Moc Toe", "FOOTWEAR", "1950s_WORKWEAR", bd("300"),
                        null, "LEATHER", "PLAIN",
                        "875 클래식 목 토", "레드윙 875 오로 레거시 목토 부츠",
                        "875 クラシックモックトゥ", "875 オロレガシー モックトゥブーツ",
                        "Iconic 6-inch moc toe boot in Oro Legacy leather with Traction Tred sole",
                        cc("Oro Legacy", "#b87333")),
                pt("8111 Iron Ranger", "FOOTWEAR", "1940s_MILITARY", bd("350"),
                        null, "LEATHER", "PLAIN",
                        "8111 아이언 레인저", "레드윙 아이언 레인저 앰버 하네스",
                        "8111 アイアンレンジャー", "アイアンレンジャー アンバーハーネス",
                        "Cap-toe ranger boot in Amber Harness leather with Vibram mini-lug sole",
                        cc("Amber", "#ff8f00")),
                pt("8085 Iron Ranger Copper", "FOOTWEAR", "1940s_MILITARY", bd("350"),
                        null, "LEATHER", "PLAIN",
                        "8085 아이언 레인저 코퍼", "레드윙 아이언 레인저 코퍼 러프앤터프",
                        "8085 アイアンレンジャー コッパー", "アイアンレンジャー コッパーラフ&タフ",
                        "Iron Ranger in Copper Rough & Tough leather with Vibram 430 sole",
                        cc("Copper", "#bf360c")),
                pt("9011 Beckman Round", "FOOTWEAR", "1950s_WORKWEAR", bd("380"),
                        null, "LEATHER", "PLAIN",
                        "9011 베크만 라운드", "레드윙 베크만 라운드 블랙 체리 페더스톤",
                        "9011 ベックマンラウンド", "ベックマンラウンド ブラックチェリーフェザーストーン",
                        "Beckman 6-inch round toe in Black Cherry Featherstone with Roccia sole",
                        cc("Black Cherry", "#880e4f")),
                pt("8883 Classic Moc Concrete", "FOOTWEAR", "1950s_WORKWEAR", bd("300"),
                        null, "LEATHER", "PLAIN",
                        "8883 클래식 목 콘크리트", "레드윙 목토 콘크리트 러프앤터프",
                        "8883 クラシックモック コンクリート", "モックトゥ コンクリートラフ&タフ",
                        "Classic moc toe in Concrete Rough & Tough leather",
                        cc("Concrete", "#9e9e9e")),
                pt("8138 Briar Oil Slick Moc", "FOOTWEAR", "1950s_WORKWEAR", bd("300"),
                        null, "LEATHER", "PLAIN",
                        "8138 브라이어 오일 슬릭 목", "레드윙 브라이어 오일슬릭 목토",
                        "8138 ブライヤーオイルスリックモック", "ブライヤーオイルスリック モックトゥ",
                        "6-inch moc toe in Briar Oil Slick pull-up leather",
                        cc("Briar", "#4e342e")),
                pt("3345 Blacksmith Copper", "FOOTWEAR", "1950s_WORKWEAR", bd("330"),
                        null, "LEATHER", "PLAIN",
                        "3345 블랙스미스 코퍼", "레드윙 블랙스미스 코퍼 러프앤터프",
                        "3345 ブラックスミス コッパー", "ブラックスミス コッパーラフ&タフ",
                        "Blacksmith boot in Copper Rough & Tough with mini-lug Vibram sole",
                        cc("Copper", "#bf360c")),
                pt("Red Wing Boot Care Kit", "ACCESSORY", "1950s_WORKWEAR", bd("35"),
                        null, null, null,
                        "레드윙 부트 케어 키트", "레드윙 오리지널 부트 케어 키트",
                        "レッドウィング ブーツケアキット", "オリジナル ブーツケアキット",
                        "Complete boot care kit with conditioner, protector, and horsehair brush",
                        cc("Natural", "#d7ccc8")),
                pt("Red Wing Leather Belt", "ACCESSORY", "1950s_WORKWEAR", bd("85"),
                        null, "LEATHER", "PLAIN",
                        "레드윙 레더 벨트", "레드윙 베지터블 태닝 레더 벨트",
                        "レッドウィング レザーベルト", "ベジタブルタンニング レザーベルト",
                        "Vegetable-tanned leather belt with Heritage series buckle",
                        cc("Oro Russet", "#bf360c"), cc("Black", "#000000"))
        );
    }

    private List<ProductTemplate> visvimProducts() {
        return List.of(
                pt("Social Sculpture 01 Slim", "DENIM", "1970s_VINTAGE", bd("795"),
                        bd("14.0"), "DENIM", "SELVEDGE",
                        "소셜 스컬프쳐 01 슬림", "비즈빔 셀비지 데님 슬림 스트레이트",
                        "ソーシャルスカルプチャー 01 スリム", "セルビッジデニム スリムストレート",
                        "Deep-indigo selvedge denim with distressed detailing and leather patch",
                        cc("Indigo", "#1a237e")),
                pt("FBT Shaman Folk", "FOOTWEAR", "1970s_VINTAGE", bd("895"),
                        null, "LEATHER", "PLAIN",
                        "FBT 샤먼 포크", "비즈빔 FBT 샤먼 포크 크리스토퍼 재블",
                        "FBT シャーマンフォーク", "FBT シャーマンフォーク クリストファーソール",
                        "FBT moccasin with hand-sewn Goodyear welt and Vibram Christy sole",
                        cc("Dark Brown", "#3e2723"), cc("Black", "#000000")),
                pt("Iris Liner Jacket", "JACKET", "1970s_VINTAGE", bd("1250"),
                        null, "COTTON", "PLAIN",
                        "아이리스 라이너 재킷", "비즈빔 아이리스 라이너 자수 재킷",
                        "アイリスライナージャケット", "アイリスライナー 刺繍ジャケット",
                        "Hand-embroidered liner jacket with traditional Japanese boro patchwork",
                        cc("Navy", "#0d47a1")),
                pt("Lhamo Shirt ICT", "SHIRT", "1970s_VINTAGE", bd("595"),
                        null, "COTTON", "PLAIN",
                        "라모 셔츠 ICT", "비즈빔 라모 ICT 오버다이 셔츠",
                        "ラモシャツ ICT", "ラモ ICT オーバーダイシャツ",
                        "Oversized Lhamo shirt in ICT-dyed cotton with Tibetan-inspired closure",
                        cc("Indigo", "#1a237e"), cc("Black", "#000000")),
                pt("Jumbo Hoodie PO", "KNIT", "1970s_VINTAGE", bd("495"),
                        null, "COTTON", "PLAIN",
                        "점보 후디 PO", "비즈빔 점보 풀오버 후디",
                        "ジャンボフーディ PO", "ジャンボ プルオーバーフーディ",
                        "Oversized pullover hoodie in heavy loopback cotton terry",
                        cc("Grey", "#9e9e9e"), cc("Black", "#212121")),
                pt("Noragi Down Jacket", "OUTERWEAR", "1970s_VINTAGE", bd("1495"),
                        null, "COTTON", "PLAIN",
                        "노라기 다운 재킷", "비즈빔 노라기 다운 필드 재킷",
                        "ノラギダウンジャケット", "ノラギ ダウンフィールドジャケット",
                        "Noragi-silhouette down jacket with GORE-TEX INFINIUM lining",
                        cc("Olive", "#33691e")),
                pt("Skagway Lo Canvas", "FOOTWEAR", "1960s_AMERICANA", bd("395"),
                        null, "CANVAS", "PLAIN",
                        "스카그웨이 로 캔버스", "비즈빔 스카그웨이 로 캔버스 스니커즈",
                        "スカグウェイ ロー キャンバス", "スカグウェイ ロー キャンバススニーカー",
                        "Low-top canvas sneaker with hand-distressed finish and gum sole",
                        cc("Off White", "#f5f5dc"), cc("Black", "#212121")),
                pt("Veggie Dye Bandana", "ACCESSORY", "1970s_VINTAGE", bd("125"),
                        null, "COTTON", "PLAIN",
                        "베지 다이 반다나", "비즈빔 식물 염색 반다나",
                        "ベジダイ バンダナ", "草木染め バンダナ",
                        "Vegetable-dyed bandana using plant-based pigments",
                        cc("Indigo", "#1a237e"), cc("Brown", "#5d4037")),
                pt("Dugout Shirt Indigo", "SHIRT", "1960s_AMERICANA", bd("475"),
                        null, "COTTON", "PLAIN",
                        "더그아웃 셔츠 인디고", "비즈빔 더그아웃 인디고 베이스볼 셔츠",
                        "ダグアウトシャツ インディゴ", "ダグアウト インディゴベースボールシャツ",
                        "Baseball-style shirt in indigo-dyed cotton with raglan sleeves",
                        cc("Indigo", "#1a237e"))
        );
    }

    private List<ProductTemplate> kapitalProducts() {
        return List.of(
                pt("Century Denim 5P Monkey Cisco", "DENIM", "1970s_VINTAGE", bd("545"),
                        bd("14.0"), "DENIM", "SELVEDGE",
                        "센추리 데님 5P 몽키 시스코", "캐피탈 센추리 데님 사시코 스티치 진",
                        "センチュリーデニム 5P モンキーシスコ", "センチュリーデニム 刺し子ステッチジーンズ",
                        "Century denim jeans with hand-stitched sashiko repair and natural indigo layers",
                        cc("Indigo", "#1a237e")),
                pt("Boro Patchwork Jacket", "JACKET", "1970s_VINTAGE", bd("895"),
                        null, "DENIM", "PLAIN",
                        "보로 패치워크 재킷", "캐피탈 보로 패치워크 데님 재킷",
                        "ボロパッチワークジャケット", "ボロパッチワーク デニムジャケット",
                        "Boro patchwork jacket assembled from vintage indigo textile fragments",
                        cc("Mixed Indigo", "#283593")),
                pt("IDG Fleece Snap Cardigan", "KNIT", "1970s_VINTAGE", bd("395"),
                        null, "COTTON", "PLAIN",
                        "IDG 플리스 스냅 가디건", "캐피탈 인디고 플리스 스냅 가디건",
                        "IDGフリース スナップカーディガン", "インディゴフリース スナップカーディガン",
                        "Indigo-dyed fleece snap cardigan with smile embroidery",
                        cc("Indigo", "#1a237e")),
                pt("Sashiko Trucker Hat", "ACCESSORY", "1970s_VINTAGE", bd("95"),
                        null, "COTTON", "PLAIN",
                        "사시코 트럭커 햇", "캐피탈 사시코 스티치 트럭커 캡",
                        "刺し子トラッカーハット", "刺し子ステッチ トラッカーキャップ",
                        "Trucker hat with sashiko-stitched front panel and mesh back",
                        cc("Indigo", "#1a237e"), cc("Black", "#212121")),
                pt("KOUNTRY Noragi Jacket", "JACKET", "1970s_VINTAGE", bd("650"),
                        null, "COTTON", "PLAIN",
                        "카운트리 노라기 재킷", "캐피탈 카운트리 인디고 노라기",
                        "KOUNTRY ノラギジャケット", "KOUNTRY インディゴノラギ",
                        "Noragi work jacket in hand-dyed indigo cotton with bound seams",
                        cc("Dark Indigo", "#1a237e")),
                pt("Ring Coat Sashiko", "OUTERWEAR", "1970s_VINTAGE", bd("1200"),
                        null, "COTTON", "PLAIN",
                        "링 코트 사시코", "캐피탈 링 코트 사시코 스티치",
                        "リングコート 刺し子", "リングコート 刺し子ステッチ",
                        "Long ring coat with all-over sashiko stitching and mixed indigo fabrics",
                        cc("Indigo", "#1a237e")),
                pt("Katsuragi Sarouel Pants", "PANTS", "1970s_VINTAGE", bd("345"),
                        null, "COTTON", "TWILL",
                        "카츠라기 사루엘 팬츠", "캐피탈 카츠라기 사루엘 드롭 크로치 팬츠",
                        "カツラギサルエルパンツ", "カツラギ サルエルドロップクロッチパンツ",
                        "Drop-crotch sarouel pants in katsuragi cotton twill",
                        cc("Navy", "#0d47a1"), cc("Black", "#212121")),
                pt("Bandana Patchwork Shirt", "SHIRT", "1970s_VINTAGE", bd("425"),
                        null, "COTTON", "PLAIN",
                        "반다나 패치워크 셔츠", "캐피탈 반다나 패치워크 웨스턴 셔츠",
                        "バンダナパッチワークシャツ", "バンダナパッチワーク ウエスタンシャツ",
                        "Western shirt assembled from vintage bandana fabrics",
                        cc("Multi", "#e91e63")),
                pt("KAPITAL Leather Pigskin Belt", "ACCESSORY", "1970s_VINTAGE", bd("165"),
                        null, "LEATHER", "PLAIN",
                        "캐피탈 피그스킨 레더 벨트", "캐피탈 인디고 피그스킨 벨트",
                        "KAPITALピッグスキンレザーベルト", "インディゴ ピッグスキンベルト",
                        "Indigo-dyed pigskin leather belt with brass ring buckle",
                        cc("Indigo", "#1a237e"), cc("Brown", "#5d4037")),
                pt("KAPITAL 60 Socks", "ACCESSORY", "1970s_VINTAGE", bd("45"),
                        null, "COTTON", "PLAIN",
                        "캐피탈 60 양말", "캐피탈 스마일리 니트 양말",
                        "KAPITAL 60ソックス", "スマイリーニットソックス",
                        "Knit socks with signature smiley embroidery at cuff",
                        cc("Navy", "#0d47a1"), cc("Red", "#c62828"), cc("Black", "#212121"))
        );
    }

    // --- Helper types and methods ---

    private record ProductTemplate(
            String nameEn, String category, String era, BigDecimal priceUsd,
            BigDecimal fabricWeightOz, String fabricType, String fabricWeave,
            String nameKo, String descKo, String nameJa, String descJa, String descEn,
            String[][] colors
    ) {}

    private static ProductTemplate pt(String nameEn, String category, String era, BigDecimal priceUsd,
                                       BigDecimal fabricWeightOz, String fabricType, String fabricWeave,
                                       String nameKo, String descKo, String nameJa, String descJa,
                                       String descEn, String[][]... colors) {
        List<String[]> colorList = new ArrayList<>();
        for (String[][] c : colors) {
            colorList.addAll(List.of(c));
        }
        return new ProductTemplate(nameEn, category, era, priceUsd, fabricWeightOz, fabricType, fabricWeave,
                nameKo, descKo, nameJa, descJa, descEn, colorList.toArray(new String[0][]));
    }

    private static String[][] cc(String name, String hex) {
        return new String[][]{{name, hex}};
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private String[] getSizesForCategory(String category) {
        return switch (category) {
            case "DENIM", "PANTS" -> DENIM_SIZES;
            case "FOOTWEAR" -> FOOTWEAR_SIZES;
            default -> APPAREL_SIZES;
        };
    }

    private Measurements getMeasurements(String category, String size) {
        return switch (category) {
            case "DENIM", "PANTS" -> denimMeasurements(size);
            case "FOOTWEAR" -> footwearMeasurements(size);
            default -> apparelMeasurements(size);
        };
    }

    private record Measurements(
            BigDecimal chest, BigDecimal shoulder, BigDecimal sleeve, BigDecimal bodyLength,
            BigDecimal waist, BigDecimal inseam, BigDecimal thigh, BigDecimal hem
    ) {}

    private Measurements denimMeasurements(String size) {
        int waistInch = Integer.parseInt(size);
        BigDecimal waist = bd(String.valueOf(waistInch * 2.54)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal inseam = bd("81.0");
        BigDecimal thigh = waist.multiply(bd("0.42")).setScale(1, RoundingMode.HALF_UP);
        BigDecimal hem = bd(String.valueOf(16.0 + (waistInch - 28) * 0.3)).setScale(1, RoundingMode.HALF_UP);
        return new Measurements(null, null, null, null, waist, inseam, thigh, hem);
    }

    private Measurements footwearMeasurements(String size) {
        double numericSize = Double.parseDouble(size);
        BigDecimal footLength = bd(String.valueOf(23.5 + (numericSize - 7) * 0.66)).setScale(1, RoundingMode.HALF_UP);
        return new Measurements(null, null, null, footLength, null, null, null, null);
    }

    private Measurements apparelMeasurements(String size) {
        int index = switch (size) {
            case "XS" -> 0;
            case "S" -> 1;
            case "M" -> 2;
            case "L" -> 3;
            case "XL" -> 4;
            case "XXL" -> 5;
            default -> 2;
        };
        BigDecimal chest = bd(String.valueOf(90 + index * 5)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal shoulder = bd(String.valueOf(41 + index * 2)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal sleeve = bd(String.valueOf(58 + index * 1.5)).setScale(1, RoundingMode.HALF_UP);
        BigDecimal bodyLength = bd(String.valueOf(63 + index * 2)).setScale(1, RoundingMode.HALF_UP);
        return new Measurements(chest, shoulder, sleeve, bodyLength, null, null, null, null);
    }

    private static int rand(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }
}
