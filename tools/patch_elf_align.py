#!/usr/bin/env python3
"""
Patch ELF64 shared objects PT_LOAD p_align to 16KB (0x4000).
Usage: python tools/patch_elf_align.py <so-file> [<so-file>...]
If a directory is given, patch all .so files recursively.
Prints before/after alignments.
"""
import sys
import os
import struct
from pathlib import Path

def patch_file(path: Path, align=0x4000):
    # backup original
    bak = path.with_suffix(path.suffix + '.orig')
    if not bak.exists():
        path.replace(bak)
        bak.replace(path)
    with path.open('r+b') as f:
        data = f.read()
        # ELF header
        if len(data) < 0x40:
            print(f"{path}: too small to be ELF64")
            return False
        if data[0:4] != b"\x7fELF":
            print(f"{path}: not ELF")
            return False
        ei_class = data[4]
        if ei_class != 2:
            print(f"{path}: not ELF64 (class={ei_class})")
            return False
        # little endian check
        ei_data = data[5]
        little = (ei_data == 1)
        endian = '<' if little else '>'
        # e_phoff at offset 32 (0x20), 8 bytes
        e_phoff = struct.unpack(endian + 'Q', data[0x20:0x28])[0]
        e_phentsize = struct.unpack(endian + 'H', data[0x36:0x38])[0]
        e_phnum = struct.unpack(endian + 'H', data[0x38:0x3A])[0]
        if e_phnum == 0:
            print(f"{path}: no program headers")
            return False
        changed = False
        for i in range(e_phnum):
            ph_offset = e_phoff + i * e_phentsize
            ph = data[ph_offset:ph_offset+e_phentsize]
            if len(ph) < e_phentsize:
                print(f"{path}: truncated ph")
                break
            # Elf64_Phdr: uint32 p_type; uint32 p_flags; uint64 p_offset; uint64 p_vaddr; uint64 p_paddr; uint64 p_filesz; uint64 p_memsz; uint64 p_align
            p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack(endian + 'IIQQQQQQ', ph[:struct.calcsize('IIQQQQQQ')])
            if p_type == 1:  # PT_LOAD
                print(f"{path}: PT_LOAD idx={i} old_align=0x{p_align:x}")
                if p_align != align:
                    # write new align
                    new_ph = struct.pack(endian + 'IIQQQQQQ', p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, align)
                    f.seek(ph_offset)
                    f.write(new_ph)
                    changed = True
                    print(f"{path}: PT_LOAD idx={i} new_align=0x{align:x}")
        if changed:
            print(f"{path}: patched.")
        else:
            print(f"{path}: no changes needed.")
        return changed


def find_so_files(path: Path):
    if path.is_dir():
        return list(path.rglob('*.so'))
    elif path.is_file():
        return [path]
    return []

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: patch_elf_align.py <so-file-or-dir> [<so-file-or-dir> ...]")
        sys.exit(2)
    any_changed = False
    for arg in sys.argv[1:]:
        p = Path(arg)
        files = find_so_files(p)
        if not files:
            print(f"No .so files found for {arg}")
            continue
        for f in files:
            try:
                changed = patch_file(f)
                any_changed = any_changed or changed
            except Exception as e:
                print(f"Error patching {f}: {e}")
    sys.exit(0 if any_changed else 0)