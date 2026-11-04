#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>

#define S 80
#define OUT 72
#define PB 64

const int8_t ff_hevc_qpel_filters[4][16] = {
    {0},
    {-1, 4, -10, 58, 17, -5, 1, 0},
    {-1, 4, -11, 40, 40, -11, 4, -1},
    {0, 1, -5, 17, 58, -10, 4, -1},
};

void ff_hevc_put_qpel_uni_w_hv_10_neon(uint8_t *, ptrdiff_t,
    const uint8_t *, ptrdiff_t, int, int, int, int, intptr_t, intptr_t, int);
void ff_hevc_put_qpel_bi_w_hv_10_neon(uint8_t *, ptrdiff_t,
    const uint8_t *, ptrdiff_t, const int16_t *, int, int, int, int,
    int, int, intptr_t, intptr_t, int);

static uint32_t seed = 123456789;
static uint32_t random32(void)
{
    seed ^= seed << 13;
    seed ^= seed >> 17;
    seed ^= seed << 5;
    return seed;
}

static uint16_t clip(int value)
{
    return value < 0 ? 0 : value > 1023 ? 1023 : value;
}

static int sample(const uint16_t *src, int stride, int x, int y,
                  const int8_t *hf, const int8_t *vf)
{
    int vertical = 0;
    for (int j = 0; j < 8; j++) {
        int horizontal = 0;
        for (int i = 0; i < 8; i++)
            horizontal += hf[i] * src[(y + j - 3) * stride + x + i - 3];
        vertical += vf[j] * (horizontal >> 2);
    }
    return vertical >> 6;
}

int main(void)
{
    uint16_t src[S * S], dst[OUT * OUT], expected[OUT * OUT];
    int16_t src2[PB * PB];
    const int widths[] = {2, 4, 6, 8, 12, 16, 24, 32, 48, 64};
    const int heights[] = {4, 7, 16, 32, 64};
    int count = 0;

    for (int wi = 0; wi < 10; wi++)
    for (int hi = 0; hi < 5; hi++)
    for (int mx = 1; mx <= 3; mx++)
    for (int my = 1; my <= 3; my++)
    for (int trial = 0; trial < 4; trial++) {
        int width = widths[wi], height = heights[hi];
        int denom = random32() & 7;
        int wx0 = (int)(random32() & 255) - 128;
        int wx1 = (int)(random32() & 255) - 128;
        int ox0 = (int)(random32() & 255) - 128;
        int ox1 = (int)(random32() & 255) - 128;
        const uint16_t *start = src + 3 * S + 3;
        int us = denom + 4, bs = denom + 5;
        int uoffset = 1 << (us - 1);
        int boffset = ((ox0 + ox1) * 4 + 1) * (1 << (bs - 1));
        for (int i = 0; i < S * S; i++) src[i] = random32() & 1023;
        for (int i = 0; i < PB * PB; i++) src2[i] = (int)(random32() & 32767) - 16384;
        memset(dst, 0xaa, sizeof(dst));
        memset(expected, 0xaa, sizeof(expected));
        ff_hevc_put_qpel_uni_w_hv_10_neon((uint8_t *)dst, OUT * 2,
            (const uint8_t *)start, S * 2, height, denom, wx1, ox1,
            mx, my, width);
        for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++) {
            int v = sample(start, S, x, y, ff_hevc_qpel_filters[mx],
                           ff_hevc_qpel_filters[my]);
            expected[y * OUT + x] = clip(((v * wx1 + uoffset) >> us) + ox1 * 4);
        }
        if (memcmp(dst, expected, sizeof(dst))) {
            fprintf(stderr, "uni mismatch w=%d h=%d mx=%d my=%d trial=%d\n",
                    width, height, mx, my, trial);
            return 1;
        }
        memset(dst, 0xaa, sizeof(dst));
        memset(expected, 0xaa, sizeof(expected));
        ff_hevc_put_qpel_bi_w_hv_10_neon((uint8_t *)dst, OUT * 2,
            (const uint8_t *)start, S * 2, src2, height, denom,
            wx0, wx1, ox0, ox1, mx, my, width);
        for (int y = 0; y < height; y++)
        for (int x = 0; x < width; x++) {
            int v = sample(start, S, x, y, ff_hevc_qpel_filters[mx],
                           ff_hevc_qpel_filters[my]);
            expected[y * OUT + x] = clip((v * wx1 + src2[y * PB + x] * wx0 +
                                          boffset) >> bs);
        }
        if (memcmp(dst, expected, sizeof(dst))) {
            fprintf(stderr, "bi mismatch w=%d h=%d mx=%d my=%d trial=%d\n",
                    width, height, mx, my, trial);
            return 1;
        }
        count += 2;
    }
    printf("PASS %d bit-exact cases\n", count);
    return 0;
}
